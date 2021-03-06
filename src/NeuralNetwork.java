import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.*;

public class NeuralNetwork {
    public static final double LEARNING_ACCURACY = 0.95;
    private static final double CLASSIFICATION_TARGET_MALE = 0.9;
    private static final double CLASSIFICATION_TARGET_FEMALE = 0.1;
    private static final int NUM_INPUT_NODES = 8100;
    private static final int NUM_HIDDEN_NODES = 60;
    private static final double LEARNING_FACTOR = 0.3;
    private static final double INTIAL_WEIGHT_VALUE_CLAMP = 0.5;

    private final ArrayList<Double> inputLayer = new ArrayList<>();
    private final ArrayList<HiddenNeuron> hiddenLayer = new ArrayList<>();
    private final ArrayList<Double> hiddenLayerOutputs = new ArrayList<>();
    private final OutputNeuron outputLayer = new OutputNeuron(NUM_HIDDEN_NODES, hiddenLayerOutputs, INTIAL_WEIGHT_VALUE_CLAMP);

    {
        for (int i = 0; i < NUM_HIDDEN_NODES; i++) {
            hiddenLayer.add(new HiddenNeuron(NUM_INPUT_NODES, inputLayer, INTIAL_WEIGHT_VALUE_CLAMP));
        }
    }

    public double trainNetwork(List<Data> data) {
        int matches = 0;
        for (int i = 0; i < data.size(); i++) {
            readInputs(data.get(i).file);
            computeOutput();
            double certainty = classify();
            //System.out.println(genders.get(i) + " " + certainty);
            if (data.get(i).gender == (certainty > 0))
                matches++;
            if (data.get(i).gender)
                updateWeights(CLASSIFICATION_TARGET_MALE);
            else
                updateWeights(CLASSIFICATION_TARGET_FEMALE);
        }

        double accuracy = ((double) matches) / ((double) data.size());
        return accuracy;
    }

    public void testNetwork(ArrayList<File> files) {
        int match=0;
        for (File file : files) {
            readInputs(file);
            computeOutput();
            double certainty = classify();
            if (certainty > 0) {
                if(file.getName().toLowerCase().contains("male") && !file.getName().toLowerCase().contains("female"))
                    match++;
                System.out.println(file.getName() + ": " + "MALE " + String.format("%.2f", certainty));
            } else {
                if(file.getName().toLowerCase().contains("female"))
                    match++;
                System.out.println(file.getName() + ": " + "FEMALE " + String.format("%.2f", -certainty));
            }
        }
        System.out.printf("Matched %d for %d files = %.2f",match,files.size(),(double)match/(double)files.size());
    }

    public double testNetwork(ArrayList<File> files, ArrayList<Boolean> genders) {
        int matches = 0;
        for (int i = 0; i < files.size(); i++) {
            readInputs(files.get(i));
            computeOutput();
            if (genders.get(i) == (classify() > 0))
                matches++;
        }

        double accuracy = ((double) matches) / ((double) files.size());
        return accuracy;
    }

    public void validate(ArrayList<File> files, ArrayList<Boolean> genders) {
        int foldSize = files.size() / 5;
        ArrayList<File> trainingFiles, testFiles;
        ArrayList<Boolean> trainingGenders, testGenders;
        for (int i = 0; i < 10; i++) {
            double trainingMean = 0.0;
            double testMean = 0.0;
            double trainingM2 = 0.0;
            double testM2 = 0.0;

            long seed = System.nanoTime();
            Collections.shuffle(files, new Random(seed));
            Collections.shuffle(genders, new Random(seed));
            int max = 5;
            for (int j = 0; j < max; j++) {
                testFiles = new ArrayList<>(files.subList(j * foldSize, j == max-1 ? files.size() : (j + 1) * foldSize));
                testGenders = new ArrayList<>(genders.subList(j * foldSize, j == max-1 ? genders.size() : (j + 1) * foldSize));
                trainingFiles = new ArrayList<>(files);
                trainingFiles.subList(j * foldSize, j == max-1 ? files.size() : (j + 1) * foldSize).clear();
                trainingGenders = new ArrayList<>(genders);
                trainingGenders.subList(j * foldSize, j == max-1 ? genders.size() : (j + 1) * foldSize).clear();
                List<Data> data = new ArrayList<>();
                for (int k = 0; k < trainingFiles.size(); k++) {
                    data.add(new Data(trainingFiles.get(k),trainingGenders.get(i)));
                }
                double trainingAccuracy = trainNetwork(data);
                double trainingDelta = trainingAccuracy - trainingMean;
                trainingMean += trainingDelta / (j + 1);
                trainingM2 += trainingDelta * (trainingAccuracy - trainingMean);

                double testAccuracy = testNetwork(testFiles, testGenders);
                double testDelta = testAccuracy - testMean;
                testMean += testDelta / (j + 1);
                testM2 += testDelta * (testAccuracy - testMean);
            }

            double trainingSD = Math.sqrt(trainingM2 / (max-1));
            double testSD = Math.sqrt(testM2 / (max-1));

            System.out.println("TEST " + (i + 1) + " RESULTS");
            System.out.println("Trainging Mean: " + trainingMean + " Training Standard Deviation: " + trainingSD);
            System.out.println("Test Mean: " + testMean + " Test Standard Deviation: " + testSD);
        }
    }

    public void saveWeights(FileOutputStream fout) {
        for (Neuron neuron : hiddenLayer) {
            neuron.saveWeights(fout);
        }
        outputLayer.saveWeights(fout);
    }

    public void loadWeights(FileInputStream fin) {
        for (Neuron neuron : hiddenLayer) {
            neuron.loadWeights(fin);
        }
        outputLayer.loadWeights(fin);
    }

    private void updateWeights(double targetOutput) {
        outputLayer.computeGradient(targetOutput);
        for (int i = 0; i < NUM_HIDDEN_NODES; i++) {
            hiddenLayer.get(i).computeGradient(targetOutput, outputLayer.getWeight(i), outputLayer.getGradient());
            hiddenLayer.get(i).updateWeights(LEARNING_FACTOR);
        }
        outputLayer.updateWeights(LEARNING_FACTOR);
    }

    private void computeOutput() {
        hiddenLayerOutputs.clear();
        for (Neuron neuron : hiddenLayer) {
            neuron.computeOutput();
            hiddenLayerOutputs.add(neuron.getOutput());
        }
        outputLayer.computeOutput();
    }

    private double getOutput() {
        return outputLayer.getOutput();
    }

    private void readInputs(File file) {
        inputLayer.clear();
        try (Scanner scanner = new Scanner(file)) {
            while (scanner.hasNext()) {
                String[] line = scanner.nextLine().split(" ");
                for (String value : line)
                    inputLayer.add((double) Integer.parseInt(value));

            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }

    private double classify() {
        double output = getOutput() - 0.5;
        double maxDeviation = 0.5;
        double certainty;

        certainty = 100 * (output / maxDeviation);
        return certainty;

    }
}
