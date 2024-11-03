/**
 * Étudiants:
 * - Cédric Colin
 * - Marvyn Levin
 *
 * Groupe: S1 B1 G5
 *
 * Cette classe permet de générer un fichier audio .wav à partir d'un message texte.
 */


import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;


public class DosSend {
    /**
     * Fréquence d'échantillonnage
     */
    static final int FECH = 44100; // fréquence d'échantillonnage
    /**
     * Fréquence de la porteuse
     */
    static final int FP = 1000;
    /**
     * Débit en symboles par seconde
     */
    static final int BAUDS = 100;  // débit en symboles par seconde
    /**
     * Format des données
     */
    static final int FMT = 16;    // format des données
    /**
     * Amplitude maximale
     */
    static final int MAX_AMP = (1<<(FMT-1))-1; // amplitude max en entier
    /**
     * Nombre de voies audio
     */
    static final int CHANNELS = 1; // nombre de voies audio (1 = mono)
    /**
     * Séquence de synchro au début
     */
    static final int[] START_SEQ = {1,0,1,0,1,0,1,0}; // séquence de synchro au début
    /**
     * Scanner pour lire le fichier texte
     */
    static final Scanner input = new Scanner(System.in); // pour lire le fichier texte

    /**
     * Nombre d'octets de données à transmettre
     */
    long taille;                // nombre d'octets de données à transmettre
    /**
     * Durée de l'audio
     */
    double duree ;              // durée de l'audio
    /**
     * Données modulées
     */
    double[] dataMod;           // données modulées
    /**
     * Données en char
     */
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
            writeLittleEndian((int) nbBytes, 4, outStream);
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
            // On calcule sur combien d'octets sera encodé chaque échantillon
            // En divisant FMT (nombre de bits par caractère) par 8 permet d'obtenir le nombre d'octets dans 8.
            int sampleSize = FMT / 8;

            // On écrit toutes les séquences normalisées
            for (double sample: dataMod)
                writeLittleEndian((int) sample, sampleSize, outStream);

            outStream.close();
        } catch (Exception e) {
            System.out.println("Erreur d'écriture : " + e.getMessage());
        }
    }

    /**
     * Read the text data to encode and store them into dataChar
     * @return the number of characters read
     */
    public int readTextData() {
        if (System.console() != null) {
            // Aucune redirection d'entrée, lire depuis la saisie standard
            System.out.print("Quel est votre message: ");
        }
        this.dataChar = input.nextLine().trim().toCharArray();
        return this.dataChar.length;
    }

    /**
     * convert a char array to a bit array
     * @param chars The chars to convert
     * @return byte array containing only 0 & 1
     */
    public byte[] charToBits(char[] chars) {
        if(chars == null)
            throw new IllegalArgumentException("Input character array should not be null.");

        // On définit une list de 0 dont la taille est le nombre de caractères multipliés par le nombre de bits réservés pour chaque caractère
        // Cette list contiendra la séquence binaire totale
        byte[] bits = new byte[chars.length * FMT];

        // Nous parcourons chaque caractère
        for (int i = 0; i < chars.length; i++) {
            // Pour chaque caractère, on part de 0 et on itère jusqu'à FMT - 1 (compris).
            // À chaque itération, 'j' est le bit que nous allons enregistrer.
            for (int j = 0; j < FMT; j++) {
                // On récupère le bit à l'indice 'j' et on effectue un complément à 1.
                int bit = (chars[i] >> j) & 1;
                // On enregistre l'indice
                bits[i * FMT + j] = (byte) bit;
            }
        }

        return bits;
    }


    /**
     * Modulate the data to send and apply the symbol throughput via BAUDS and FECH.
     * @param bits the data to modulate
     */
    public void modulateData(byte[] bits) {
        // On calcule le nombre d'échantillons par symbole et le nombre d'échantillons totaux
        int samplesPerSymbol = FECH / BAUDS;
        int totalSample = (int) (duree * FECH);

        // On définit dataMod comme une list composée de 0 et de taille totalSample, soit le nombre d'échantillons
        dataMod = new double[totalSample];

        int sampleIndex = 0;
        // On parcourt chaque bit de la séquence de départ
        for (int seqBit: START_SEQ) {
            if (seqBit == 0) {
                // si le bit est à 0, on n'a pas besoin d'écrire nos échantillons
                // On ajoute à notre index le nombre d'échantillons pour un bit
                sampleIndex += samplesPerSymbol;
            } else {
                // Sinon, on va itérer de 0 au nombre de symboles par bit
                for (int i = 0; i < samplesPerSymbol; i++) {
                    dataMod[sampleIndex++] = MAX_AMP * Math.sin(2 * Math.PI * FP * i / FECH);
                }
            }
        }

        // Nous effectuons la même opération que précédemment pour la séquence binaire
        for (byte bit: bits){
            if (bit == 0) {
                sampleIndex += samplesPerSymbol;
            } else {
                for (int i = 0; i < samplesPerSymbol; i++) {
                    dataMod[sampleIndex++] = MAX_AMP * Math.sin(2 * Math.PI * FP * i / FECH);
                }
            }
        }
    }

    static final double Y_AXIS_PADDING = 0.75;

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
        double seqPeriod = (double) stop - (double) start;
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
        StdDraw.setTitle(title);
        int n = stop - start;

        if (n <= 0) {
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

    /**
     * Main function
     *
     * @param args The arguments passed to the program
     */
    public static void main(String[] args) {
        // créé un objet DosSend
        DosSend dosSend = new DosSend("DosOok_message.wav");

        // lit le texte à envoyer depuis l'entrée standard
        // et calcule la durée de l'audio correspondant selon le nombre de bits réservés à chaque caractère
        dosSend.duree = (dosSend.readTextData() + START_SEQ.length / (double) FMT) * FMT / BAUDS;

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