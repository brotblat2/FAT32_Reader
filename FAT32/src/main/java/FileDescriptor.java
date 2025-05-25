import java.util.ArrayList;
import java.util.List;

 class FileDescriptor {
    String name;
    boolean isDirectory;
    int firstCluster;
    int fileSize;
    int attr;

    // Attribute flags
    boolean isReadOnly;
    boolean isHidden;
    boolean isSystem;
    boolean isVolumeID;
    boolean isArchive;

    public FileDescriptor(String name, boolean isDirectory, int firstCluster, int fileSize, int attr) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.firstCluster = firstCluster;
        this.fileSize = fileSize;
        this.attr = attr;

        // Decode attributes
        isReadOnly = (attr & 0x01) != 0;
        isHidden   = (attr & 0x02) != 0;
        isSystem   = (attr & 0x04) != 0;
        isVolumeID = (attr & 0x08) != 0;
        isArchive  = (attr & 0x20) != 0;
    }

    @Override
    public String toString() {
        return (isDirectory ? "[DIR] " : "[FILE] ") + name +
                " (Cluster: " + firstCluster + ", Size: " + fileSize + ")";
    }
    public String getAttributeString() {
        List<String> attrs = new ArrayList<>();

        if (isArchive)   attrs.add("ATTR_ARCHIVE");
        if (isDirectory) attrs.add("ATTR_DIRECTORY");
        if (isVolumeID)  attrs.add("ATTR_VOLUME_ID");
        if (isSystem)    attrs.add("ATTR_SYSTEM");
        if (isHidden)    attrs.add("ATTR_HIDDEN");
        if (isReadOnly)  attrs.add("ATTR_READ_ONLY");

        if (attrs.isEmpty()) {
            return "NONE";
        } else {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < attrs.size(); i++) {
                result.append(attrs.get(i));
                if (i != attrs.size() - 1) {
                    result.append(" ");
                }
            }
            return result.toString();
        }
    }
}