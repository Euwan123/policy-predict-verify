import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CsvLoader {
    public static List<LoginRecord> load(File file) throws Exception {
        List<LoginRecord> records = new ArrayList<>();
        List<String> lines = Files.readAllLines(file.toPath());
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length < 3) continue;
            records.add(new LoginRecord(parts[0].trim(), parts[1].trim(), Integer.parseInt(parts[2].trim())));
        }
        return records;
    }
}
