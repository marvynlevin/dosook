public class LPFilter1 {
    public double[] lpFilter(double[] inputSignal, double samplingRate, double cutoffFrequency) {
        double resistance = 1.0 / (cutoffFrequency * 2 * Math.PI);
        double timeStep = 1.0 / samplingRate;
        double smoothingFactor = timeStep / (resistance + timeStep);
        double[] filteredOutput = new double[inputSignal.length];

        // Valeur initiale
        filteredOutput[0] = inputSignal[0];

        // On boucle sur le filtre
        for (int i = 1; i < inputSignal.length; i++) {
            filteredOutput[i] = smoothingFactor * inputSignal[i] + (1 - smoothingFactor) * filteredOutput[i - 1];
        }

        return filteredOutput;
    }
}
