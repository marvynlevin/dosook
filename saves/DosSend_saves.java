import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public class DosSend {
    final int FECH = 44100; // fréquence d'échantillonnage
    /**
     * Fréquence de la porteuse
     */
    final int FP = 1000;
    final int BAUDS = 100;  // débit en symboles par seconde
    final int FMT = 16;    // format des données
    final int MAX_AMP = (1<<(FMT-1))-1; // amplitude max en entier
    final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    final int[] START_SEQ = {1,0,1,0,1,0,1,0}; // séquence de synchro au début
    final Scanner input = new Scanner(System.in); // pour lire le fichier texte

    long taille;                // nombre d'octets de données à transmettre
    double duree ;              // durée de l'audio
    double[] dataMod;           // données modulées
    char[] dataChar;            // données en char

    FileOutputStream outStream; // flux de sortie pour le fichier .wav

    /**
     * Constructor
     * @param path  the path of the wav file to create
     */
    public DosSend(String path){
        File file = new File(path);
        try{
            outStream = new FileOutputStream(file);
        } catch (Exception e) {
            System.out.println("Erreur de création du fichier");
        }
    }

    /**
     * Write a raw 4-byte integer in little endian
     * @param octets    the integer to write
     * @param destStream  the stream to write in
     */
    public void writeLittleEndian(int octets, int taille, FileOutputStream destStream){
        char poidsFaible;
        while(taille > 0){
            poidsFaible = (char) (octets & 0xFF);
            try {
                destStream.write(poidsFaible);
            } catch (Exception e) {
                System.out.println("Erreur d'écriture");
            }
            octets = octets >> 8;
            taille--;
        }
    }


    /**
     * Create and write the header of a wav file
     */
    public void writeWavHeader() {
        taille = (long) (FECH * duree);
        long nbBytes = taille * CHANNELS * FMT / 8;
        try {
            outStream.write("RIFF".getBytes());
            writeLittleEndian((int) (nbBytes - 8), 4, outStream);
            outStream.write("WAVE".getBytes());

            outStream.write("fmt ".getBytes());
            writeLittleEndian(16, 4, outStream);

            writeLittleEndian(1, 2, outStream);
            writeLittleEndian(CHANNELS, 2, outStream);

            writeLittleEndian(FECH, 4, outStream);
            writeLittleEndian((FECH * CHANNELS * FMT) / 8, 4, outStream);
            writeLittleEndian(CHANNELS * FMT / 8, 2, outStream);
            writeLittleEndian(FMT, 2, outStream);

            // data sub-chunk
            outStream.write("data".getBytes());
            writeLittleEndian((int) nbBytes - 44, 4, outStream);
        } catch (IOException e) {
            System.out.println("Could not write wave file - " + e.getMessage());
        }
    }

    /**
     * Write the data in the wav file
     * after normalizing its amplitude to the maximum value of the format (8 bits signed)
     */
    public void writeNormalizeWavData() {
        try {
            double maxAmplitude = Arrays.stream(dataMod).max().orElse(1.0);
            for (double sample: dataMod) {
                int normalizedSample = (int) ((sample / maxAmplitude) * MAX_AMP);
                writeLittleEndian(normalizedSample, FMT / 8, outStream);
            }
            outStream.close();
        } catch (Exception e) {
            System.out.println("Erreur d'écriture : " + e.getMessage());
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     * @return the number of characters read
     */
    public int readTextData(){
        if (this.dataChar == null) {
            this.dataChar = new char[0];
        }

        int charCount = 0;
        while (input.hasNext()) {
            String l = input.nextLine();
            int lLength = l.length();
            charCount += lLength;

            // Count the newline character if it's not the last line
            if (input.hasNext()) {
                charCount++;
            }

            char[] dataCharNew = new char[dataChar.length + lLength + 1];
            System.arraycopy(dataChar, 0, dataCharNew, 0, dataChar.length);
            l.getChars(0, lLength, dataCharNew, dataChar.length);

            // Add a newline character if it's not the last line
            if (input.hasNext()) {
                dataCharNew[dataChar.length + lLength] = '\n';
            }

            dataChar = dataCharNew;
        }

        return charCount;
    }

    /**
     * convert a char array to a bit array
     * @param chars The chars to convert
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars) {
        if(chars == null) {
            throw new IllegalArgumentException("Input character array should not be null.");
        }

        byte[] bits = new byte[chars.length * FMT];

        for (int i = 0; i < chars.length; i++) {
            for (int j = 0; j < FMT; j++) {
                bits[i * FMT + j] = (byte) (chars[i] >> j & 1);
            }
        }
        return bits;
    }


    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits) {
        int samplesPerSymbol = FECH / BAUDS;
        int totalSample = (int) (duree * FECH);

        dataMod = new double[totalSample];

        int sampleIndex = 0;

        for (int seqBit: START_SEQ) {
            int amplitude = seqBit == 1 ? MAX_AMP : 0;
            for (int i = 0; i < samplesPerSymbol && sampleIndex < totalSample; i++) {
                dataMod[sampleIndex++] = amplitude * Math.sin(2 * Math.PI * FP * i / FECH);
            }
        }

        for (byte bit: bits){
            int amplitude = bit == 1 ? MAX_AMP : 0;
            for (int i = 0; i < samplesPerSymbol && sampleIndex < totalSample; i++) {
                dataMod[sampleIndex++] = amplitude * Math.sin(2 * Math.PI * FP * i / FECH);
            }
        }
    }

    public static final double Y_AXIS_PADDING = 0.75;

    /**
     * Display a signal in a window
     * @param sig The signal to display
     * @param start The first sample to display
     * @param stop The last sample to display
     * @param mode "line" or "point"
     * @param title The title of the window
     */
    public static void displaySig(double[] sig, int start, int stop, String mode, String title){
        int length = sig.length;
        if (length == 0) {
            return; // No need to display an empty signal
        }

        double max = Arrays.stream(sig, start, stop).max().orElse(1.0);
        double min = Arrays.stream(sig, start, stop).min().orElse(0.0);

        int height = 500;
        int width  = 1000;

        StdDraw.setCanvasSize(width, height);
        StdDraw.setXscale(0.0, width);
        StdDraw.setYscale(min, max);

        // draw min & max
        StdDraw.text(50, max - (max / 1000 * 50), String.valueOf((int) max));
        StdDraw.text(50, min + (Math.abs(min) / 1000 * 50),         String.valueOf((int) min));

        // draw middle line with numbers
        StdDraw.line(0, 0.0, width, 0.0);
        double seqPeriod = stop - start;
        for (int i = 0; i < 10; i++) {
            int x = (width / 10) * i;
            String n = String.valueOf((seqPeriod / 10) * i);

            StdDraw.line(x, (max / 1000 * 5), x, (max / 1000 * -5));
            StdDraw.text(x, (max / 1000 * -60), n);
        }

        // draw lines
        double paddingRatio = 0.8;
        if ("line".equals(mode)) {
            StdDraw.setPenColor(Color.BLUE);
            for (int i = start; i < stop - 1; i++) {
                double seq1 = sig[i];
                double x1 = ((double) width / (stop - start)) * (i - start);

                double seq2 = sig[i + 1];
                double x2 = ((double) width / (stop - start)) * (i + 1 - start);

                StdDraw.line(x1, seq1 * paddingRatio, x2, seq2 *paddingRatio);
            }
        } else {
            for (int i = start; i < stop; i++) {
                double seq = sig[i];
                double x = ((double) width / (stop - start)) * (i - start);

                StdDraw.point(x, seq * paddingRatio);
            }
        }
    }


    /**
     * Display signals in a window
     * @param listOfSigs A list of the signals to display
     * @param start The first sample to display
     * @param stop The last sample to display
     * @param mode "line" or "point"
     * @param title The title of the window
     */
    public static void displaySig(List<double[]> listOfSigs, int start, int stop, String mode, String title){
        int N = stop - start;

        if (N <= 0) {
            return; // No need to display an empty signal range
        }

        double maxVal = listOfSigs.stream()
                .flatMapToDouble(sig -> Arrays.stream(sig, start, stop))
                .max()
                .orElse(1.0);
        double minVal = listOfSigs.stream()
                .flatMapToDouble(sig -> Arrays.stream(sig, start, stop))
                .min()
                .orElse(0.0);

        double yRange = maxVal - minVal;
        double padding = Y_AXIS_PADDING * yRange;

        StdDraw.setCanvasSize(700, 500);
        StdDraw.setXscale(start, stop - 1);
        StdDraw.setYscale(minVal - padding, maxVal + padding);

        int numOfSigs = listOfSigs.size();

        StdDraw.setPenColor(StdDraw.BLUE);
        for (int s = 0; s < numOfSigs; s++) {
            double[] sig = listOfSigs.get(s);

            if ("line".equals(mode)) {
                // Draw using lines
                for (int i = start + 1; i < stop && i < sig.length; i++) {
                    StdDraw.line(i - 1, sig[i - 1], i, sig[i]);
                }
            } else {
                // Draw using points
                for (int i = start; i < stop && i < sig.length; i++) {
                    StdDraw.point(i, sig[i]);
                }
            }
        }
    }

    public static void main(String[] args) {
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");

        // lit le texte à envoyer depuis l'entrée standard
        // et calcule la durée de l'audio correspondant
        dosSend.duree = (double) (dosSend.readTextData() + dosSend.START_SEQ.length / 8) * 8.0 / dosSend.BAUDS;

        // génère le signal modulé après avoir converti les données en bits
        dosSend.modulateData(dosSend.charToBits(dosSend.dataChar));

        // écrit l'entête du fichier wav
        dosSend.writeWavHeader();

        // écrit les données audio dans le fichier wav
        dosSend.writeNormalizeWavData();

        // affiche les caractéristiques du signal dans la console
        System.out.println("Message : "+String.valueOf(dosSend.dataChar));
        System.out.println("\tNombre de symboles : "+dosSend.dataChar.length);
        System.out.println("\tNombre d'échantillons : "+dosSend.dataMod.length);
        System.out.println("\tDurée : "+dosSend.duree+" s");
        System.out.println();


        // exemple d'affichage du signal modulé dans une fenêtre graphique
        displaySig(dosSend.dataMod, 1000, 3000, "line", "Signal modulé");
    }

}