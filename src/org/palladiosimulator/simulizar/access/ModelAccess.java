package org.palladiosimulator.simulizar.access;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.eclipse.emf.common.notify.Notification;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.palladiosimulator.commons.emfutils.EMFCopyHelper;
import org.palladiosimulator.monitorrepository.MonitorRepository;
import org.palladiosimulator.runtimemeasurement.RuntimeMeasurementFactory;
import org.palladiosimulator.runtimemeasurement.RuntimeMeasurementModel;
import org.palladiosimulator.simulizar.interpreter.listener.ReconfigurationEvent;
import org.palladiosimulator.simulizar.launcher.jobs.LoadMonitorRepositoryModelIntoBlackboardJob;
import org.palladiosimulator.simulizar.launcher.partitions.MonitorRepositoryResourceSetPartition;
import org.palladiosimulator.simulizar.reconfiguration.IReconfigurationListener;
import org.scaledl.usageevolution.UsageEvolution;
import org.scaledl.usageevolution.UsageevolutionPackage;

import de.uka.ipd.sdq.simucomframework.SimuComSimProcess;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.MDSDBlackboard;
import de.uka.ipd.sdq.workflow.mdsd.blackboard.ResourceSetPartition;
import de.uka.ipd.sdq.workflow.pcm.blackboard.PCMResourceSetPartition;
import de.uka.ipd.sdq.workflow.pcm.jobs.LoadPCMModelsIntoBlackboardJob;

/**
 * Helper to access the PCM model (global and local), the RuntimeMeasurement model, the Monitor
 * Repository model, the usage evolution model and all SD models.
 * 
 * @author Joachim Meyer, Steffen Becker, Erlend Stav
 */
public class ModelAccess implements IModelAccess, IReconfigurationListener {

    private static final Logger LOGGER = Logger.getLogger(ModelAccess.class.getName());

    private final Map<SimuComSimProcess, PCMResourceSetPartition> modelCopies = new HashMap<SimuComSimProcess, PCMResourceSetPartition>();
    private final PCMResourceSetPartition pcmPartition;
    private PCMResourceSetPartition currentPCMCopy;
    private final MonitorRepositoryResourceSetPartition monitorRepositoryPartition;
    private final RuntimeMeasurementModel runtimeMeasurementModel;
    private final MDSDBlackboard blackboard;

    /**
     * Constructor
     * 
     * @param blackboard
     *            the workflow engine's blackboard holding all models.
     */
    public ModelAccess(final MDSDBlackboard blackboard) {
        super();
        this.blackboard = blackboard;
        this.runtimeMeasurementModel = RuntimeMeasurementFactory.eINSTANCE.createRuntimeMeasurementModel();
        this.pcmPartition = getResourceSetPartition(blackboard, LoadPCMModelsIntoBlackboardJob.PCM_MODELS_PARTITION_ID);
        this.monitorRepositoryPartition = getResourceSetPartition(blackboard,
                LoadMonitorRepositoryModelIntoBlackboardJob.MONITOR_REPOSITORY_MODEL_PARTITION_ID);
        this.currentPCMCopy = copyPCMPartition();
    }

    private ModelAccess(final ModelAccess copy) {
        super();
        this.blackboard = copy.blackboard;
        this.runtimeMeasurementModel = copy.runtimeMeasurementModel;
        this.pcmPartition = copy.pcmPartition;
        this.monitorRepositoryPartition = copy.monitorRepositoryPartition;
        this.currentPCMCopy = copy.currentPCMCopy;
    }

    @Override
    public IModelAccess clone() {
        return new ModelAccess(this);
    }

    @Override
    public PCMResourceSetPartition getLocalPCMModel() {
        return this.currentPCMCopy;
    }

    /**
     * @return a copy of the global PCM modelling partition
     */
    private PCMResourceSetPartition copyPCMPartition() {
        final PCMResourceSetPartition newPartition = new PCMResourceSetPartition();
        List<EObject> modelCopy = EMFCopyHelper.deepCopyToEObjectList(pcmPartition.getResourceSet());
        for (int i = 0; i < modelCopy.size(); i++) {
            Resource resource = newPartition.getResourceSet().createResource(URI.createFileURI("/temp" + i));
            resource.getContents().add(modelCopy.get(i));
        }
        return newPartition;
    }

    @Override
    public PCMResourceSetPartition getGlobalPCMModel() {
        return this.pcmPartition;
    }

    /**
     * 
     * @return the global Monitor Repository model.
     */
    @Override
    public MonitorRepository getMonitorRepositoryModel() {
        return monitorRepositoryPartition.getMonitorRepositoryModel();
    }

    /**
     * 
     * @return the global RuntimeMeasurement model.
     */
    @Override
    public RuntimeMeasurementModel getRuntimeMeasurementModel() {
        return this.runtimeMeasurementModel;
    }

    /**
     * 
     * @return the global usage evolution model, or null if no such model is available
     */
    @Override
    public UsageEvolution getUsageEvolutionModel() {
        try {
            LOGGER.debug("Retrieving Usage Evolution model from blackboard partition");
            List<UsageEvolution> result = this.pcmPartition.getElement(UsageevolutionPackage.eINSTANCE
                    .getUsageEvolution());
            // List<UsageEvolution> result =
            // this.uePartititon.getElement(UsageevolutionPackage.eINSTANCE
            // .getUsageEvolution());
            return result.get(0);
        } catch (Exception e) {
            LOGGER.info("No Usage Evolution model found, so evolution will not be simulated.");
            return null;
        }
    }

    /**
     * Checks whether Monitor Repository exists.
     * 
     * @return true if yes, otherwise false;
     */
    public boolean monitorRepositoryExists() {
        return monitorRepositoryPartition.getResourceSet().getResources().size() > 0;
    }

    @SuppressWarnings("unchecked")
    private <T extends ResourceSetPartition> T getResourceSetPartition(final MDSDBlackboard blackboard, final String id) {
        return (T) blackboard.getPartition(id);
    }

    @Override
    public void reconfigurationExecuted(final Collection<Notification> modelChanges) {
        LOGGER.debug("Reconfiguration(s) have been exectuted, taking a new copy of the global PCM for new simulation threads");
        this.currentPCMCopy = copyPCMPartition();
    }

    @Override
    public void beginReconfigurationEvent(ReconfigurationEvent event) {
        // Nothing to do
    }

    @Override
    public void endReconfigurationEvent(ReconfigurationEvent event) {
        // Nothing to do
    }

    @Override
    public MDSDBlackboard getBlackboard() {
        return this.blackboard;
    }

}
