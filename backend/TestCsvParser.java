import com.opencsv.CSVReader;
import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;

public class TestCsvParser {
    public static void main(String[] args) throws Exception {
        try (CSVReader csvReader = new CSVReader(new FileReader("src/test/resources/razorpay_recon.csv"))) {
            String[] headers = csvReader.readNext();
            Map<String, Integer> headerMap = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerMap.put(headers[i].trim().toLowerCase(), i);
            }

            String[] line;
            while ((line = csvReader.readNext()) != null) {
                if (line.length == 0 || (line.length == 1 && line[0].trim().isEmpty())) continue;
                
                String gatewayTransactionId = line[headerMap.get("payment_id")];
                System.out.println("Payment ID: " + gatewayTransactionId);
            }
        }
    }
}
