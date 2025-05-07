
import java.io.File;

public class Chatbot {

    private static OpenAiAssistantEngine assistantAcademicAdvisor;
    private static String prompt;
    private static float totalTime = 0f;
    private static int totalTokens = 0;
    private static float averageTimePerPrompt = 0f;
    private static int averageTokensPerPrompt = 0;
    private static int prompts = 0;
    private static final String APIKEY = System.getenv"OPENAI_API_KEY";

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

        //System.out.println(assistantAcademicAdvisor.getFileContents());
        //example prompts
        prompt = "What College, Department, and Major am I in?";
        getData();

        prompt = "What grade do I need in the class CS375 to get the credit?";
        getData();

        prompt = "What concentration am I in?";
        getData();

        prompt = "What other concentrations are available for my major?";
        getData();

        //this is just to show the total time, total tokens, and averages to document the performance of the assistant
        outputFinals();

        //best practice to clear all before starting or when finished, it helps with the response time and accuracy when using large files, long prompts (or many prompts), and/or many tokens (and cached tokens)
        assistantAcademicAdvisor.clearAllCachedTokens();

        //this is just to show a method that bypasses all predetermined conditions that get parsed into the prompt
        //generally this should only be used for super general questions as it has NONE of the assistant's context or features
        //this also does not do any user message or returned message parsing or verification but it still does have message formatting available
        //this can be useful for getting a quick response to a question that is not in the assistant's training data
        //or for queries that are not meant to be in the scope of the chat history or file contents
        // System.out.println(assistant.forceDirectChatGPT("What is the average GPA for students in my major?", false));
        // System.out.println("-------------------------");
        // System.out.println(assistant.forceDirectChatGPT("Create a python program that calculates fibonacci numbers.", true));
        //
        //this is just a test to show how the assistant can be used by a user
        userTest();
    }

    private static void getData() {
        try {
            System.out.println(prompt);
            System.out.println();
            System.out.println("-------------------------");
            System.out.println();
            System.out.println(assistantAcademicAdvisor.chatGPT(prompt, true));
            float time = Float.parseFloat(assistantAcademicAdvisor.getResponseData("processing_time_ms")) / 1000f;
            totalTime += time;
            totalTokens += Integer.parseInt(assistantAcademicAdvisor.getResponseData("total_tokens"));
            System.out.println();
            System.out.println("-------------------------");
            System.out.println();
            prompts++;
        } catch (NumberFormatException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void outputFinals() {
        try {
            System.out.println("Total time: " + totalTime + " seconds");
            System.out.println("Total tokens: " + totalTokens);
            averageTimePerPrompt = totalTime / prompts;
            averageTokensPerPrompt = totalTokens / prompts;
            System.out.println("Average time per prompt: " + averageTimePerPrompt + " seconds");
            System.out.println("Average tokens per prompt: " + averageTokensPerPrompt);
            System.out.println();
        } catch (ArithmeticException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void userTest() {
        prompts = 0;
        assistantAcademicAdvisor.clearChatCache();
        float floatSeconds = 0f;
        int intTokens = 0;
        System.out.println("This is a demonstration of the assistant in a practical use case, it assumes the user is already logged in.");
        String input;
        while (true) {
            System.out.print("~");
            input = System.console().readLine();
            prompts++;
            if (input.equals("exit")) {
                System.out.println("Total time: " + floatSeconds + " seconds");
                System.out.println("Total tokens: " + intTokens);
                System.out.println("Average time per prompt: " + (floatSeconds / prompts) + " seconds");
                System.out.println("Average tokens per prompt: " + (intTokens / prompts));
                break;
            }
            System.out.println("-------------------------");
            System.out.println(assistantAcademicAdvisor.chatGPT(input, true));
            System.out.println("-------------------------");
            floatSeconds += Float.parseFloat(assistantAcademicAdvisor.getResponseData("processing_time_ms")) / 1000f;
            intTokens += Integer.parseInt(assistantAcademicAdvisor.getResponseData("total_tokens"));
        }
    }
}
