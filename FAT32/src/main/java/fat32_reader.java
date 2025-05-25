import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.*;

public class fat32_reader {
    static int BPB_BytesPerSec;  // Bytes per sector (usually 512)
    static int BPB_SecPerClus;   // Sectors per cluster (must be a power of 2)
    static int BPB_RsvdSecCnt;   // Reserved sectors before the FAT (includes boot sector)
    static int BPB_NumFATs;      // Number of FAT tables (usually 2)
    static int BPB_FATSz32;      // Sectors per FAT
    static int BPB_RootClus;     // Cluster number of the root directory (usually 2)


    public static void main(String[] args) throws IOException {
        Scanner scanner = new Scanner(System.in);

        String fileName=args[0];
        byte[] buffer = new byte[64];
        FileInputStream fis = new FileInputStream(fileName);

        fis.read(buffer, 0, 64);
        //read the root cluster and fill in the class variables
        loadInfo(buffer);

        Map<Integer, Map<String, FileDescriptor>> directories = new HashMap<>();

        int currentCluster = BPB_RootClus;

        byte[] rootClusterBytes = readCluster(fileName, BPB_RootClus);

        Map<String, FileDescriptor> rootFiles = parseDirectory(rootClusterBytes);
        directories.put(BPB_RootClus, rootFiles);
        LinkedList<String> pwd = new LinkedList<>();
        LinkedList<Integer> clusterStack = new LinkedList<>();
        LinkedList<FileDescriptor> dirStack = new LinkedList<>();

        pwd.add("");
        clusterStack.add(BPB_RootClus);
        FileDescriptor rootFile = new FileDescriptor("/", true, BPB_RootClus, 0, 0x10);  // fake stat info for root
        dirStack.add(rootFile);


        while (true) {

            for (int i=0;i<pwd.size();i++){
                String s=pwd.get(i);
                System.out.print(s);

                if ( i!=pwd.size()-1 || pwd.size()==1 ){
                    System.out.print("/");
                }
            }
            System.out.print("] ");
            String input = scanner.nextLine().trim().toUpperCase();

            if (input.equals("STOP")) {
                break;
            }
            if (input.equals("INFO")) {
                info();
            }
            if (input.equals("LS")) {
                List<String> fileNames = new ArrayList<>();
                //not sure how to add this stuff here

                Map<String, FileDescriptor> currentDir = directories.get(currentCluster);
                for (FileDescriptor projFile : currentDir.values()) {
                    fileNames.add(projFile.name);
                }
                if (!fileNames.contains(".")) fileNames.add(".");
                if (!fileNames.contains("..")) fileNames.add("..");

                Collections.sort(fileNames);

                for (String name : fileNames) {
                    System.out.print(name + " ");
                }
                System.out.println();
            }

            if (input.startsWith("STAT")) {
                String[] stats = input.split(" ", 2);
                if (stats.length < 2) {
                    System.out.println("Error: No filename provided");
                    continue;
                }

                String name = stats[1].trim().toUpperCase();

                FileDescriptor pf = null;

                if (name.equals(".")) {
                    pf = dirStack.getLast();
                } else if (name.equals("..")) {
                    if (dirStack.size() > 1) {
                        pf = dirStack.get(dirStack.size() - 2);
                    }
                }
                if (pf == null && !name.equals(".") && !name.equals("..")) {
                    pf = directories.get(currentCluster).get(name);
                }

                if (pf == null) {
                    System.out.println("Error: file/directory does not exist");
                    continue;
                }

                System.out.println("Size is " + pf.fileSize);
                System.out.println("Attributes " + pf.getAttributeString());
                System.out.printf("Next cluster number is 0x%08X\n", pf.firstCluster);
            }

            if (input.startsWith("SIZE")) {
                String[] sizes = input.split(" ", 2);

                if (sizes.length < 2) {
                    System.out.println("Error: No filename provided");
                    continue;
                }
                String name = sizes[1].trim().toUpperCase();
                if (name.equals(".") || name.equals("..")) {
                    System.out.println("Error: " + sizes[1] + " is not a file");
                }
                FileDescriptor pf = directories.get(currentCluster).get(name);
                if (pf == null || pf.isDirectory) {
                    System.out.println("Error: " + sizes[1] + " is not a file");
                }
                else{
                    System.out.println("Size of " + pf.name + " is " + pf.fileSize + " bytes");
                }
            }

            if (input.startsWith("CD")) {
                String[] cd = input.split(" ", 2);

                if (cd.length < 2) {
                    System.out.println("Error: No filename provided");
                    continue;
                }
                String name = cd[1].trim().toUpperCase();

                if (name.equals(".")) continue;
                else if (name.equals("..")){
                    if(pwd.size()==1) {// in the root, can't go up one
                        System.out.println("Error: " + name + " is not a directory");
                        continue;
                    }
                    pwd.removeLast();
                    clusterStack.removeLast();
                    currentCluster=clusterStack.getLast();
                    dirStack.removeLast();
                    continue;
                }
                //actually input a word
                FileDescriptor pf = directories.get(currentCluster).get(name);
                 if (pf == null || !pf.isDirectory) {
                    System.out.println("Error: " + name + " is not a directory");
                }
                else{
                     currentCluster = pf.firstCluster;
                    pwd.addLast(pf.name);
                    clusterStack.addLast(currentCluster);
                    dirStack.addLast(pf);


                     if (!directories.containsKey(currentCluster)) {
                         Map<String, FileDescriptor> files = readDirectoryChain(fileName, currentCluster);
                         directories.put(currentCluster, files);
                     }
                }
            }

            if (input.startsWith("READ")) {
                String[] reads = input.split(" ", 4);

                if (reads.length < 4) {
                    System.out.println("Error: Not enough inputs provided");
                    continue;
                }
                if (!isInteger(reads[3]) || !isInteger(reads[2])){
                    System.out.println("Error: Wrong data type input");
                    continue;
                }
                String name = reads[1].trim().toUpperCase();
                int offset = Integer.parseInt(reads[2].trim());
                int numBytes = Integer.parseInt(reads[3].trim());
                if (offset < 0) {
                    System.out.println("Error: OFFSET must be a positive value");
                    continue;
                }
                if (numBytes <= 0) {
                    System.out.println("Error: NUM_BYTES must be greater than zero");
                    continue;
                }
                FileDescriptor pf = directories.get(currentCluster).get(name);
                if (pf == null || pf.isDirectory) {
                    System.out.println("Error: " + name + " is not a file");
                    continue;
                }
                if (offset + numBytes > pf.fileSize) {
                    System.out.println("Error: attempt to read data outside of file bounds");
                    continue;
                }
               System.out.println(readBytes(fileName, pf.firstCluster, offset, numBytes));
            }
        }

        scanner.close();
    }

    public static boolean isInteger(String str) {
        if (str == null || str.isEmpty()) return false;
        try {
            Integer.parseInt(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }



    private static void loadInfo(byte[] buffer) {
        BPB_BytesPerSec = (buffer[0x0B] & 0xFF) | ((buffer[0x0C] & 0xFF) << 8);
        BPB_SecPerClus  = buffer[0x0D] & 0xFF;
        BPB_RsvdSecCnt  = (buffer[0x0E] & 0xFF) | ((buffer[0x0F] & 0xFF) << 8);
        BPB_NumFATs     = buffer[0x10] & 0xFF;
        BPB_FATSz32     = (buffer[0x24] & 0xFF) | ((buffer[0x25] & 0xFF) << 8) |
                ((buffer[0x26] & 0xFF) << 16) | ((buffer[0x27] & 0xFF) << 24);
        BPB_RootClus    = (buffer[0x2C] & 0xFF) | ((buffer[0x2D] & 0xFF) << 8) |
                ((buffer[0x2E] & 0xFF) << 16) | ((buffer[0x2F] & 0xFF) << 24);
    }

    private static void info() {
        System.out.println("BPB_BytesPerSec is " + toHex(BPB_BytesPerSec) + ", " + BPB_BytesPerSec);
        System.out.println("BPB_SecPerClus is " + toHex(BPB_SecPerClus) + ", " + BPB_SecPerClus);
        System.out.println("BPB_RsvdSecCnt is " + toHex(BPB_RsvdSecCnt) + ", " + BPB_RsvdSecCnt);
        System.out.println("BPB_NumFATs is " + toHex(BPB_NumFATs) + ", " + BPB_NumFATs);
        System.out.println("BPB_FATSz32 is " + toHex(BPB_FATSz32) + ", " + BPB_FATSz32);
    }

    private static String toHex(int value) {
        return String.format("0x%x", value);
    }

    //look into this method.
    public static HashMap<String, FileDescriptor> parseDirectory(byte[] sectorBuffer) {
        HashMap<String, FileDescriptor> files = new HashMap<>();

        for (int offset = 0; offset < sectorBuffer.length; offset += 32) {
            byte firstByte = sectorBuffer[offset];

            // 0x00 means no more entries; 0xE5 means unused
            if (firstByte == 0x00) break;
            if ((firstByte & 0xFF) == 0xE5) continue;

            int attr = sectorBuffer[offset + 11] & 0xFF;
            if (attr == 0x0F || (attr & 0x08) != 0) continue;
            boolean isDirectory = (attr & 0x10) != 0;

            // Read short name
            String name = new String(sectorBuffer, offset, 8).trim();
            String ext = new String(sectorBuffer, offset + 8, 3).trim();
            if (!ext.isEmpty()) name += ("." + ext);

            // First cluster
            int high = (sectorBuffer[offset + 20] & 0xFF) | ((sectorBuffer[offset + 21] & 0xFF) << 8);
            int low  = (sectorBuffer[offset + 26] & 0xFF) | ((sectorBuffer[offset + 27] & 0xFF) << 8);
            int firstCluster = (high << 16) | low;

            // File size (bytes 28–31)
            int fileSize = (sectorBuffer[offset + 28] & 0xFF) |
                    ((sectorBuffer[offset + 29] & 0xFF) << 8) |
                    ((sectorBuffer[offset + 30] & 0xFF) << 16) |
                    ((sectorBuffer[offset + 31] & 0xFF) << 24);

            files.put(name.trim().toUpperCase(), new FileDescriptor(name.trim(), isDirectory, firstCluster, fileSize, attr));

        }

        return files;
    }
    private static byte[] readCluster(String fileName, int clusterNum) throws IOException {
        int firstDataSector = BPB_RsvdSecCnt + (BPB_NumFATs * BPB_FATSz32);
        int firstSectorOfCluster = firstDataSector + (clusterNum - 2) * BPB_SecPerClus;
        int byteOffset = firstSectorOfCluster * BPB_BytesPerSec;

        FileInputStream fis = new FileInputStream(fileName);
        fis.skip(byteOffset);
        byte[] buffer = new byte[BPB_SecPerClus * BPB_BytesPerSec];
        fis.read(buffer);
        fis.close();
        return buffer;
    }

    private static int readFATEntry(FileInputStream fis, int clusterNum) throws IOException {
        int fatOffset = BPB_RsvdSecCnt * BPB_BytesPerSec + clusterNum * 4;
        fis.getChannel().position(fatOffset);

        byte[] entry = new byte[4];
        fis.read(entry);
        return (entry[0] & 0xFF) |
                ((entry[1] & 0xFF) << 8) |
                ((entry[2] & 0xFF) << 16) |
                ((entry[3] & 0xFF) << 24);
    }
    private static List<Integer> getClusterChain(FileInputStream fis, int startCluster) throws IOException {
        List<Integer> chain = new ArrayList<>();
        int cluster = startCluster;

        while (cluster < 0x0FFFFFF8) {
            chain.add(cluster);
            cluster = readFATEntry(fis, cluster);
        }

        return chain;
    }
    private static Map<String, FileDescriptor> readDirectoryChain(String fileName, int startCluster) throws IOException {
        Map<String, FileDescriptor> allEntries = new HashMap<>();

        FileInputStream fis = new FileInputStream(fileName) ;
            List<Integer> clusterChain = getClusterChain(fis, startCluster);
            for (int clusterNum : clusterChain) {
                byte[] clusterBytes = readCluster(fileName, clusterNum);
                Map<String, FileDescriptor> entries = parseDirectory(clusterBytes);
                allEntries.putAll(entries);
            }


        return allEntries;
    }

    private static String readBytes(String fileName, int startCluster, int offset, int numBytes) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (FileInputStream fis = new FileInputStream(fileName)) {
            List<Integer> chain = getClusterChain(fis, startCluster);

            int bytesPerCluster = BPB_SecPerClus * BPB_BytesPerSec;
            int bytesToSkip = offset;
            int bytesRemaining = numBytes;

            for (int clusterNum : chain) {
                byte[] clusterData = readCluster(fileName, clusterNum);

                // If we still need to skip
                if (bytesToSkip >= bytesPerCluster) {
                    bytesToSkip -= bytesPerCluster;
                    continue;
                }


                int start = bytesToSkip;
                int end = Math.min(start + bytesRemaining, bytesPerCluster);

                for (int i = start; i < end; i++) {
                    int b = clusterData[i] & 0xFF;
                    if (b == 0x0A || (b >= 0x20 && b <= 0x7E)) {
                        sb.append((char) b);
                    } else {
                        sb.append(String.format(" 0x%02X ", b));
                    }
                }

                bytesRemaining -= (end - start);
                bytesToSkip = 0;

                if (bytesRemaining <= 0) break;
            }
        }

        return sb.toString();
    }

}



