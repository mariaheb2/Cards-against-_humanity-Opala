import com.google.gson.JsonParser;

public class TestGson {
    public static void main(String[] args) {
        String[] tests = {
            "[object Object]",
            "[object Uint8Array]",
            "{\"type\":\"REGISTER\"}a",
            "42[\"message\"]",
            "{" + "\"type\"".repeat(3) + "}a"
        };
        for (String test : tests) {
            System.out.println("Testing: " + test);
            try {
                JsonParser.parseString(test);
                System.out.println("Success");
            } catch (Exception e) {
                System.out.println("Fail: " + e.toString());
            }
        }
    }
}
