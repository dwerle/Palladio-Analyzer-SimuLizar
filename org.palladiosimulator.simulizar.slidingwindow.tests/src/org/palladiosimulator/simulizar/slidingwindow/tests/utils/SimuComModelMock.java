package org.palladiosimulator.simulizar.slidingwindow.tests.utils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.palladiosimulator.edp2.impl.RepositoryManager;
import org.palladiosimulator.edp2.models.Repository.LocalDirectoryRepository;
import org.palladiosimulator.experimentanalysis.SlidingWindow;
import org.palladiosimulator.probeframework.ProbeFrameworkContext;
import org.palladiosimulator.probeframework.calculator.DefaultCalculatorFactory;
import org.palladiosimulator.simulizar.simulationevents.PeriodicallyTriggeredSimulationEntity;
import org.palladiosimulator.simulizar.slidingwindow.impl.SimulizarSlidingWindow;

import de.uka.ipd.sdq.simucomframework.SimuComConfig;
import de.uka.ipd.sdq.simucomframework.model.SimuComModel;
import de.uka.ipd.sdq.simucomframework.simucomstatus.SimuComStatus;
import de.uka.ipd.sdq.simucomframework.simucomstatus.SimucomstatusFactory;

public class SimuComModelMock extends SimuComModel {

    private static LocalDirectoryRepository repo = null;
    private static String repoId = null;
    private static File repoFile = new File("testRepo");

    private static SimuComModelMock instance = null;

    private static void createRepository() {
        repo = RepositoryManager.initializeLocalDirectoryRepository(repoFile);
        repoId = repo.getId();
        RepositoryManager.addRepository(RepositoryManager.getCentralRepository(), repo);
    }

    private static void deleteRepository() {
        RepositoryManager.removeRepository(RepositoryManager.getCentralRepository(), repo);
        repo = null;
        repoId = null;
        if (repoFile.canRead() && repoFile.canWrite()) {
            if (repoFile.isDirectory()) {
                for (File f : repoFile.listFiles()) {
                    f.delete();
                }
            }
            repoFile.delete();
        }
    }

    private static Map<String, Object> createMockedConfiguration() {

        Map<String, Object> configuration = new HashMap<String, Object>();
        configuration.put(SimuComConfig.SIMULATE_FAILURES, false);
        configuration.put(SimuComConfig.SIMULATE_LINKING_RESOURCES, false);
        configuration.put(SimuComConfig.VERBOSE_LOGGING, false);
        configuration.put(SimuComConfig.VARIATION_ID, SimuComConfig.DEFAULT_VARIATION_NAME);
        configuration.put(SimuComConfig.SIMULATOR_ID, "de.uka.ipd.sdq.codegen.simucontroller.simulizar");
        configuration.put(SimuComConfig.EXPERIMENT_RUN, SimuComConfig.DEFAULT_EXPERIMENT_RUN);
        configuration.put(SimuComConfig.SIMULATION_TIME, SimuComConfig.DEFAULT_SIMULATION_TIME);
        configuration.put(SimuComConfig.MAXIMUM_MEASUREMENT_COUNT, SimuComConfig.DEFAULT_MAXIMUM_MEASUREMENT_COUNT);
        configuration.put(SimuComConfig.PERSISTENCE_RECORDER_NAME, "Experiment Data Persistency & Presentation (EDP2)");
        configuration.put(SimuComConfig.USE_FIXED_SEED, false);
        configuration.put("EDP2RepositoryID", repoId);

        return configuration;
    }

    private static SimuComStatus createSimuComStatus() {
        return SimucomstatusFactory.eINSTANCE.createSimuComStatus();
    }

    public void triggerMockWindowMoveOn(SimulizarSlidingWindow window) {
        new MockWindowMoveOnTriggeredEvent(this, window).triggerInternal();
    }

    public static SimuComModel obtainMockModel() {
        if (instance == null) {
            createRepository();
            instance = new SimuComModelMock();
        }
        return instance;
    }

    public static void releaseMockModel() {
        if (instance != null) {
            deleteRepository();
            instance = null;
        }
    }

    private SimuComModelMock() {

        super(new SimuComConfig(createMockedConfiguration(), false), createSimuComStatus(), new SimEngineFactoryMock(),
                false, new ProbeFrameworkContext(new DefaultCalculatorFactory()));
    }

    private static class MockWindowMoveOnTriggeredEvent extends PeriodicallyTriggeredSimulationEntity {
        private final SimulizarSlidingWindow window;

        private MockWindowMoveOnTriggeredEvent(SimuComModel model, SimulizarSlidingWindow window) {
            super(model, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
            this.window = window;
        }

        private Method obtainMoveOnMethod() {
            Method moveOnMethod = null;
            try {
                moveOnMethod = SlidingWindow.class.getDeclaredMethod("moveOn", new Class[0]);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (SecurityException e) {
                e.printStackTrace();
            }
            return moveOnMethod;
        }

        private void invokeMoveOn() {
            // dirty hack;)
            Method moveOnMethod = obtainMoveOnMethod();
            if (moveOnMethod != null) {
                moveOnMethod.setAccessible(true);
                try {
                    moveOnMethod.invoke(this.window, new Object[0]);
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                } catch (InvocationTargetException e) {
                    e.printStackTrace();
                }
            } else {
                throw new NoSuchMethodError("Method not found: moveOn() of class " + this.window.getClass().getName());
            }
        }

        @Override
        protected void triggerInternal() {
            invokeMoveOn();
        }
    }
}