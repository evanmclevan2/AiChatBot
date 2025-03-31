
import java.io.File;

public class Chatbot {

    private static OpenAiAssistantEngine assistantAcademicAdvisor;
    private static final String APIKEY = "you wish haha";

    public static void main(String[] args) {
        System.out.println();
        System.out.println("-------------------------");
        System.out.println();

        assistantAcademicAdvisor = new OpenAiAssistantEngine.Builder()
                .setApiKey(APIKEY)
                .setAssistantType("file-search")
                .setInitialInstruction("You are an real-time chat, AI Academic Advisor for Abilene Christian University. Address the student by their first and last name only if this is their first message to you.")
                .setFiles(new File[]{new File("user_info.txt"), new File("db/acu_database.db")})
                .setModel("gpt-4o-mini")
                .setCacheTokens(true)
                .setDynamicPromptLength(true)
                .setDynamicPromptLengthScale(10f)
                .setTimeoutFlagSeconds(10f)
                .build();
    }
}
