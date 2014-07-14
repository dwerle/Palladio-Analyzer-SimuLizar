package org.palladiosimulator.simulizar.utils;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.ecore.util.EContentAdapter;
import org.palladiosimulator.simulizar.access.IModelAccessFactory;
import org.palladiosimulator.simulizar.access.PMSAccess;
import org.palladiosimulator.simulizar.metrics.ResourceStateListener;
import org.palladiosimulator.simulizar.pms.MeasurementSpecification;
import org.palladiosimulator.simulizar.pms.PerformanceMetricEnum;
import org.palladiosimulator.simulizar.prm.PrmFactory;
import org.palladiosimulator.simulizar.prm.ResourceContainerMeasurement;

import de.uka.ipd.sdq.pcm.resourceenvironment.ProcessingResourceSpecification;
import de.uka.ipd.sdq.pcm.resourceenvironment.ResourceContainer;
import de.uka.ipd.sdq.pcm.resourceenvironment.ResourceEnvironment;
import de.uka.ipd.sdq.pcm.resourcetype.SchedulingPolicy;
import de.uka.ipd.sdq.simucomframework.ModelsAtRuntime;
import de.uka.ipd.sdq.simucomframework.model.SimuComModel;
import de.uka.ipd.sdq.simucomframework.resources.AbstractScheduledResource;
import de.uka.ipd.sdq.simucomframework.resources.ScheduledResource;
import de.uka.ipd.sdq.simucomframework.resources.SchedulingStrategy;
import de.uka.ipd.sdq.simucomframework.resources.SimulatedResourceContainer;

/**
 * Class to sync resource environment model with SimuCom. UGLY DRAFT!
 * 
 * @author Joachim Meyer, Sebastian Lehrig
 */
public class ResourceSyncer {
    protected static final Logger LOG = Logger.getLogger(ResourceSyncer.class.getName());

    private final SimuComModel simuComModel;

    private final IModelAccessFactory modelAccessFactory;

    /**
     * Constructor
     * 
     * @param simuComModel
     *            the SimuCom model.
     * @param modelAccessFactory
     *            the modelAccessFactory.
     */
    public ResourceSyncer(final SimuComModel simuComModel, final IModelAccessFactory modelAccessFactory) {
        super();
        this.simuComModel = simuComModel;
        this.modelAccessFactory = modelAccessFactory;
        final ResourceEnvironment resourceEnvironment = modelAccessFactory.getGlobalPCMAccess().getModel()
                .getResourceEnvironment();
        resourceEnvironment.eAdapters().add(new EContentAdapter() {

            @Override
            public void notifyChanged(final Notification notification) {
                super.notifyChanged(notification);
                LOG.info("Resource environment changed by reconfiguration - Resync simulated resources: "
                        + notification);
                ResourceSyncer.this.syncResourceEnvironment();
            }

        });
    }

    /**
     * @return returns the simuComModel.
     */
    private SimuComModel getSimuComModel() {
        return this.simuComModel;
    }

    /**
     * Checks whether simulated resource (by type id) already exists in given simulated resource
     * container.
     * 
     * @param simulatedResourceContainer
     *            the simulated resource container.
     * @param typeId
     *            id of the resource.
     * @return the ScheduledResource.
     */
    private ScheduledResource resourceAlreadyExist(final SimulatedResourceContainer simulatedResourceContainer,
            final String typeId) {
        // Resource already exists?
        for (final AbstractScheduledResource abstractScheduledResource : simulatedResourceContainer
                .getActiveResources()) {
            if (abstractScheduledResource.getResourceTypeId().equals(typeId)) {

                return (ScheduledResource) abstractScheduledResource;

            }
        }
        return null;
    }

    /**
     * Sync resources in resource container. If simulated resource already exists in SimuCom,
     * setProcessingRate will be updated.
     * 
     * @param resourceContainer
     *            the resource container.
     * @param simulatedResourceContainer
     *            the corresponding simulated resource container in SimuCom.
     */
    private void syncActiveResources(final ResourceContainer resourceContainer,
            final SimulatedResourceContainer simulatedResourceContainer) {

        // add resources
        for (final ProcessingResourceSpecification processingResource : resourceContainer
                .getActiveResourceSpecifications_ResourceContainer()) {
            final String typeId = processingResource.getActiveResourceType_ActiveResourceSpecification().getId();
            final String processingRate = processingResource.getProcessingRate_ProcessingResourceSpecification()
                    .getSpecification();
            // processingRate does not need to be evaluated, will be done in
            // simulatedResourceContainers

            // SchedulingStrategy
            final SchedulingPolicy schedulingPolicy = processingResource.getSchedulingPolicy();

            String schedulingStrategy = schedulingPolicy.getId();
            if (schedulingStrategy.equals("ProcessorSharing")) {
                schedulingStrategy = SchedulingStrategy.PROCESSOR_SHARING;
            } else if (schedulingStrategy.equals("FCFS")) {
                schedulingStrategy = SchedulingStrategy.FCFS;
            } else if (schedulingStrategy.equals("Delay")) {
                schedulingStrategy = SchedulingStrategy.DELAY;
            }

            final ScheduledResource scheduledResource = this.resourceAlreadyExist(simulatedResourceContainer, typeId);
            if (existsResource(scheduledResource)) {
                scheduledResource.setProcessingRate(processingRate);
            } else {
                createSimulatedActiveResource(resourceContainer, simulatedResourceContainer, processingResource,
                        schedulingStrategy);
            }
        }
    }

    /**
     * @param scheduledResource
     *            Resource which existence shall be checked
     * @return true if resource exists
     */
    private boolean existsResource(final ScheduledResource scheduledResource) {
        return scheduledResource != null;
    }

    /**
     * 
     * @param resourceContainer
     * @param simulatedResourceContainer
     * @param processingResource
     * @param schedulingStrategy
     */
    private void createSimulatedActiveResource(final ResourceContainer resourceContainer,
            final SimulatedResourceContainer simulatedResourceContainer,
            final ProcessingResourceSpecification processingResource, String schedulingStrategy) {
        simulatedResourceContainer.addActiveResource(ModelsAtRuntime.getResourceURI(processingResource),
                new String[] {}, resourceContainer.getId(), schedulingStrategy);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Added ActiveResource. TypeID: "
                    + processingResource.getActiveResourceType_ActiveResourceSpecification().getId()
                    + ", Description: " + ", SchedulingStrategy: " + schedulingStrategy);
        }

        // is monitored?
        final PMSAccess pmsAccess = this.modelAccessFactory.getPMSModelAccess();
        MeasurementSpecification measurementSpecification = pmsAccess.isMonitored(resourceContainer,
                PerformanceMetricEnum.UTILIZATION);
        if (isMonitored(measurementSpecification)) {
            final ResourceContainerMeasurement resourceContainerMeasurement = PrmFactory.eINSTANCE
                    .createResourceContainerMeasurement();
            resourceContainerMeasurement.setMeasurementSpecification(measurementSpecification);
            resourceContainerMeasurement.setPcmModelElement(resourceContainer);
            resourceContainerMeasurement.setProcessingResourceType(processingResource
                    .getActiveResourceType_ActiveResourceSpecification());

            // get created active resource
            for (final AbstractScheduledResource abstractScheduledResource : simulatedResourceContainer
                    .getActiveResources()) {
                if (abstractScheduledResource.getName().equals(processingResource.getId())) {
                    new ResourceStateListener(processingResource.getActiveResourceType_ActiveResourceSpecification(),
                            abstractScheduledResource, this.getSimuComModel(), measurementSpecification,
                            resourceContainerMeasurement, resourceContainer, processingResource,
                            this.modelAccessFactory);
                    break;
                }

            }
        }
    }

    /**
     * @param measurementSpecification
     * @return
     */
    private boolean isMonitored(MeasurementSpecification measurementSpecification) {
        return measurementSpecification != null;
    }

    public void initialiseResourceEnvironment() {
        syncResourceEnvironment();
    }

    /**
     * Syncs resource environment model with SimuCom.
     */
    private void syncResourceEnvironment() {

        // TODO this is only a draft
        if (LOG.isDebugEnabled()) {
            LOG.debug("Synchronise ResourceContainer and Simulated ResourcesContainer");
        }
        // add resource container, if not done already
        for (final ResourceContainer resourceContainer : this.modelAccessFactory.getGlobalPCMAccess().getModel()
                .getResourceEnvironment().getResourceContainer_ResourceEnvironment()) {
            final String resourceContainerId = resourceContainer.getId();

            SimulatedResourceContainer simulatedResourceContainer;
            if (this.getSimuComModel().getResourceRegistry().containsResourceContainer(resourceContainerId)) {
                simulatedResourceContainer = (SimulatedResourceContainer) this.getSimuComModel().getResourceRegistry()
                        .getResourceContainer(resourceContainerId);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("SimulatedResourceContainer already exists: " + simulatedResourceContainer);
                }
                // now sync active resources
                this.syncActiveResources(resourceContainer, simulatedResourceContainer);
            } else {
                // create
                simulatedResourceContainer = (SimulatedResourceContainer) this.getSimuComModel().getResourceRegistry()
                        .createResourceContainer(resourceContainerId);
                LOG.debug("Added SimulatedResourceContainer: ID: " + resourceContainerId + " "
                        + simulatedResourceContainer);

                // now sync active resources
                this.syncActiveResources(resourceContainer, simulatedResourceContainer);
            }

        }

        LOG.debug("Synchronisation done");
        // TODO remove unused
    }

}