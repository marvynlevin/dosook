/**
 * Étudiants:
 * - Cédric Colin
 * - Marvyn Levin
 *
 * Groupe: S1 B1 G5
 *
 * Cette class permet de lire un fichier audio wav et de décoder le message caché à l'intérieur.
 */



import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;

public class LPFilter1Etude {
    /**
     * Fréquence de la porteuse
     */
    static final int FP = 1000;
    /**
     * Débit en symboles par seconde
     */
    static final int BAUDS = 100;
    /**
     * Séquence de départ
     */
    static final int[] START_SEQ = {1,0,1,0,1,0,1,0};
    /**
     * Fichier audio
     */
    FileInputStream fileInputStream;
    /**
     * Fréquence d'échantillonnage
     */
    int sampleRate = 44100;
    /**
     * Nombre de bits par échantillon
     */
    int bitsPerSample;
    /**
     * Taille des données audio
     */
    int dataSize;
    /**
     * Tableau de doubles contenant les données audio
     */
    double[] audio;
    /**
     * Tableau d'entiers contenant les bits de sortie
     */
    int[] outputBits;
    /**
     * Tableau de caractères contenant les caractères décodés
     */
    char[] decodedChars;

    /**
     * Constructor that opens the FIlEInputStream
     * and reads sampleRate, bitsPerSample and dataSize
     * from the header of the wav file
     * @param path the path of the wav file to read
     */
    public void readWavHeader(String path){
        byte[] header = new byte[44]; // La tête fait 44 bits de longueur
        try {
            fileInputStream= new FileInputStream(path);
            fileInputStream.read(header);

            // Read the sample rate (offset 24, 4 bytes)
            sampleRate = byteArrayToInt(header, 24, 32);
            // Read the number of bits per sample (offset 34, 2 bytes)
            bitsPerSample = byteArrayToInt(header, 34, 16);
            // Read the size of the data (offset 40, 4 bytes)
            dataSize = byteArrayToInt(header, 40, 32);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Helper method to convert a little-endian byte array to an integer
     * @param bytes the byte array to convert
     * @param offset    the offset in the byte array
     * @param fmt   the format of the integer (16 or 32 bits)
     * @return  the integer value
     */
    private static int byteArrayToInt(byte[] bytes, int offset, int fmt) {
        if (fmt == 16)
            return ((bytes[offset + 1] & 0xFF) << 8) | (bytes[offset] & 0xFF);
        else if (fmt == 32)
            return ((bytes[offset + 3] & 0xFF) << 24) |
                    ((bytes[offset + 2] & 0xFF) << 16) |
                    ((bytes[offset + 1] & 0xFF) << 8) |
                    (bytes[offset] & 0xFF);
        else return (bytes[offset] & 0xFF);
    }

    /**
     * Read the audio data from the wav file
     * and convert it to an array of doubles
     * that becomes the audio attribute
     */
    public void readAudioDouble() {
        byte[] audioData = new byte[dataSize];
        double max = Math.pow(2, bitsPerSample - 1);
        try {
            int bytesRead = fileInputStream.read(audioData);

            // Crée un tableau de doubles appelé audio pour stocker les valeurs normalisées des échantillons audio.
            // La taille du tableau est déterminée en fonction du nombre d'octets par échantillon (bitsPerSample / 8)
            // et de la taille totale des données audio dans audioData.

            // On s'assure qu'audioData est un multiple de 2.
            audioData = Arrays.copyOf(audioData, bytesRead + bytesRead % 2);

            // Initialisez le tableau audio si c'est la première fois
            if (audio == null) {
                audio = new double[audioData.length / (bitsPerSample / 8)];
            }

            for (int i = 0; i < audio.length; i++) {
                int byteFaible = audioData[2 * i];
                int byteFort = audioData[2 * i + 1];

                int echantillon = (byteFort << 8) | (byteFaible & 255); // Composition d'un échantillon sur 16 bits
                audio[i] = echantillon / max; // Normalise l'échantillon sur [-1, 1]
            }
        } catch (IOException e) {
            System.out.println("Erreur de lecture des données audio: " + e.getMessage());
        }
    }


    /**
     * Reverse the negative values of the audio array
     */
    public void audioRectifier(){
        // Nous parcourons chaque fréquence dans l'audio
        for (int i = 0; i < audio.length; i++) {
            // Nous transformons les fréquences négatives en fréquences positives.
            // Nous n'avons pas utilisé Math.abs car cette méthode requiert de réécrire tous les éléments du tableau.
            // Ainsi, nous avons pris la décision de privilégier l'utilisation d'une condition, permettant d'économiser
            // des opérations d'écritures en mémoire.
            if (audio[i] < 0)
                audio[i] = -audio[i];
        }
    }

    /**
     * Apply a low pass filter to the audio array
     * Fc = (1/2n)*FECH
     * @param n the number of samples to average
     */
    public void audioLPFilter(int n) {
        // On doit être sûr que n est un nombre entier strictement positif.
        if (n <= 0)
            throw new IllegalArgumentException("Number of samples (n) must be greater than 0");

        // On crée un tableau qui contiendra notre audio filtré
        double[] filteredAudio = new double[audio.length];

        // Nous appliquons le filtre passe-bas
        for (int i = n - 1; i < audio.length; i++) {
            // On calcule la somme des fréquences audio pour cette partie
            double sum = 0;
            for (int j = 0; j < n; j++)
                sum += audio[i - j];

            // On n'a plus qu'à enregistrer dans notre liste la moyenne
            filteredAudio[i] = sum / n;
        }

        // Copy the filtered audio back to the original array
        System.arraycopy(filteredAudio, 0, audio, 0, audio.length);
    }


    /**
     * Resample the audio array and apply a threshold
     * @param period    the number of audio samples by symbol
     * @param threshold the threshold that separates 0 and 1
     */
    public void audioResampleAndThreshold(int period, int threshold) {
        double[] resampledAudio = new double[audio.length / period];

        // Cette liste contiendra les bits obtenus par l'algorithme.
        outputBits = new int[resampledAudio.length - START_SEQ.length];

        // Nous calculons la fréquence maximale.
        int max = (int) (Math.pow(2, bitsPerSample));

        for (int i = 0; i < resampledAudio.length; i++) {
            // Nous calculons la somme des fréquences audio
            double sum = 0.0;
            for (int j = 0; j < period; j++)
                sum += audio[i * period + j];

            // L'opération (sum / period) permet d'exprimer un pourcentage sur [0, 1]
            // Avec ce pourcentage, nous calculons la fréquence moyenne réelle pour cette section en multipliant par la
            // fréquence maximale.
            // Cette fréquence moyenne réelle permet de déterminer le bot à cet emplacement.
            double bit = max * (sum / period) > threshold ? 1 : 0;

            // Nous enregistrons le bit s'il n'est pas dans la séquence de début.
            if (i > START_SEQ.length - 1)
                outputBits[i - START_SEQ.length] = (int) bit;
        }
    }

    public static String timestamp(long clock0) {
        String result = null;

        if (clock0 > 0) {
            double elapsed = (System.nanoTime() - clock0) / 1e9;
            String unit = "s";
            if (elapsed < 1.0) {
                elapsed *= 1000.0;
                unit = "ms";
            }
            result = String.format("%.4g%s elapsed", elapsed, unit);
        }
        return result;
    }

    public static long timestamp() {
        return System.nanoTime();
    }

    /**
     * Decode the outputBits array to a char array
     * The decoding is done by comparing the START_SEQ with the actual beginning of outputBits.
     * The next first symbol is the first bit of the first char.
     */
    public void decodeBitsToChar() {
        // Cette condition a pour objectif de ne pas avoir de mauvaises surprises en ayant un nombre de bits incomplet
        // ou de ne pas avoir de bits du tout.

        if (outputBits.length % bitsPerSample != 0)
            throw new IllegalArgumentException("Invalid outputBits array: " + (outputBits.length) + " bytes is not a multiple of the bitsPerSample");

        // Nous définissons `decodedChars` comme un tableau de caractères vides et dont la longueur
        // est le nombre de bits divisé par le nombre de bits par caractère.
        // Cette opération est "safe" grâce à la pré-condition ci-dessus, nous sommes d'obtenir un nombre entier.
        this.decodedChars = new char[outputBits.length / bitsPerSample];

        // Parcourir la séquence binaire en groupes de `bitsPerSample` bits
        for (int i = 0; i < outputBits.length; i += bitsPerSample) {
            // Nous reconstruisons la séquence binaire pour ce caractère
            int seqSum = getSeqSum(i);

            // Finalement, nous stockons le caractère décodé dans la liste `decodedChars`
            decodedChars[i / bitsPerSample] = (char) seqSum;
        }
    }

    /**
     * Convert a group of bits to a char
     * @param i the index of the first bit of the group
     * @return the char value of the group
     *
     * @implNote Cette méthode a été ajoutée afin de simplifier la méthode `decodeBitsToChar`
     */
    private int getSeqSum(int i) {
        StringBuilder bitsGroup = new StringBuilder();
        for (int j = 0; j < bitsPerSample; j++)
            bitsGroup.append(outputBits[i + j]);

        // Convertir le groupe de bits en une liste de caractères
        char[] seqChars = bitsGroup.toString().toCharArray();

        int seqSum = 0;
        for (int j = 0; j < seqChars.length; j++) {
            seqSum += seqChars[j] == '0' ? 0 : (int) Math.pow(2, j);
        }
        return seqSum;
    }

    /**
     * Print the elements of an array
     * @param data the array to print
     */
    public static void printIntArray(char[] data) {
        for (char c : data) {
            System.out.print(c);
        }
        System.out.println();
    }

    private static void writeToFile(String filePath, double[] data) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Double row : data) {
                writer.write(row.toString().replace('.', ',')); // Remplace le point par une virgule
                writer.newLine();  // Ajoute une nouvelle ligne après chaque valeur
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * Display a signal in a window
     * @param sig The signal to display
     * @param start The first sample to display
     * @param stop The last sample to display
     * @param mode "line" or "point"
     * @param title The title of the window
     */
    public static void displaySig(double[] sig, int start, int stop, String mode, String title){
        StdDraw.setTitle(title);

        int length = sig.length;
        if (length == 0) {
            return; // No need to display an empty signal
        }

        double max = sig[0];
        for (double s: sig)
            if (s > max) max = s;

        int height = 500;
        int width  = 1000;

        StdDraw.setCanvasSize(width, height);
        StdDraw.setXscale(0.0, width);
        StdDraw.setYscale(-max, max);

        // draw min & max
        StdDraw.text(50, max - (max / 1000 * 50), String.valueOf((int) max));
        StdDraw.text(50, -max + (max / 1000 * 50),         String.valueOf((int) -max));

        // draw middle line with numbers
        StdDraw.line(0, 0.0, width, 0.0);
        double seqPeriod = (double) stop - (double) start;
        for (int i = 0; i < 10; i++) {
            int x = (width / 10) * i;
            String n = String.valueOf((int) ((seqPeriod / 10) * i));

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
     *  Un exemple de main qui doit pourvoir être exécuté avec les méthodes
     * que vous aurez conçues.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java LPFilter1Etude <input_wav_file>");
            return;
        }
        String wavFilePath = args[0];

        // Open the WAV file and read its header
        LPFilter1Etude LPFilter1Etude = new LPFilter1Etude();
        LPFilter1Etude.readWavHeader(wavFilePath);

        // Print the audio data properties
        System.out.println("Fichier audio: " + wavFilePath);
        System.out.println("\tSample Rate: " + LPFilter1Etude.sampleRate + " Hz");
        System.out.println("\tBits per Sample: " + LPFilter1Etude.bitsPerSample + " bits");
        System.out.println("\tData Size: " + LPFilter1Etude.dataSize + " bytes");

        // Read the audio data
        LPFilter1Etude.readAudioDouble();
        // reverse the negative values
        LPFilter1Etude.audioRectifier();

        writeToFile("audio1.txt", LPFilter1Etude.audio);

        // apply a low pass filter
        long now = timestamp();
        LPFilter1Etude.audioLPFilter(44);
        System.out.println("LPFilter1Etude.audioLPFilter(44) " + timestamp(now));
        // Resample audio data and apply a threshold to output only 0 & 1
        LPFilter1Etude.audioResampleAndThreshold(LPFilter1Etude.sampleRate/BAUDS, 20000);

        LPFilter1Etude.decodeBitsToChar();
        if (LPFilter1Etude.decodedChars != null){
            System.out.print("Message décodé : ");
            printIntArray(LPFilter1Etude.decodedChars);
        }
        displaySig(LPFilter1Etude.audio, 0, LPFilter1Etude.audio.length-1, "line", "Signal audio");

        // Close the file input stream
        try {
            LPFilter1Etude.fileInputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
