
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class OpenAiAssistantEngine {

    //non-default (need to be set by user inorder to function)
    private String USER_API_KEY;
    private String initialInstruction;
    private String assistantType; //either 'chat' or 'file-search' or 'code-interpreter'
    private File[] files; //only needed if assistant type is set to 'file-search' or 'code-interpreter'

    //default (can be set by user)
    private int maxPromptLength = -1;
    private boolean cacheTokens = false;
    private boolean maxPromptPrecision = false;
    private boolean dynamicPromptLength = false;
    private float dynamicPromptLengthScale = 5;
    private float timeoutFlagSeconds = 60;
    private String currentModel = "gpt-3.5-turbo";

    //internal file management
    private File[] lastUsedFiles;
    private String fileContents;

    //internal chat management
    private List<String> chatCache;
    private String lastPromptUsed;
    private String lastResponseReceived;
    private Map<String, Object> responseDataMap;

    //database connection
    private Connection dbConnection;

    // Constructors
    public OpenAiAssistantEngine(String apiKey, String assistantType, String initialInstrction) {
        initialize(apiKey, assistantType, initialInstrction, null);
    }

    public OpenAiAssistantEngine(String apiKey, String assistantType, String initialInstrction, File[] userFiles) {
        initialize(apiKey, assistantType, initialInstrction, userFiles);
    }

    public OpenAiAssistantEngine(String jsonString) {
        this(new JSONObject(jsonString));
    }

    public OpenAiAssistantEngine(JSONObject jsonConfig) {
        this.USER_API_KEY = jsonConfig.optString("apikey", null);
        this.assistantType = jsonConfig.optString("assistantType", "chat");
        this.initialInstruction = jsonConfig.optString("instruction", null);
        this.chatCache = new ArrayList<>();

        JSONArray filesArray = jsonConfig.optJSONArray("files");
        if (filesArray != null) {
            this.files = new File[filesArray.length()];
            for (int i = 0; i < filesArray.length(); i++) {
                this.files[i] = new File(filesArray.getString(i));
            }
            lastUsedFiles = files;
            processFileContents();
        }
    }

    public OpenAiAssistantEngine() {
        initialize(null, null, null, null);
    }

    // Getters and Setters
    public void setAPIKey(String apiKey) {
        this.USER_API_KEY = apiKey;
    }

    public String getAPIKey() {
        return USER_API_KEY;
    }

    public void setInititalInstruction(String instruction) {
        this.initialInstruction = instruction;
    }

    public String getInitialInstruction() {
        return initialInstruction;
    }

    public void setAssistantType(String assistantType) {
        this.assistantType = assistantType;
    }

    public String getAssistantType() {
        return assistantType;
    }

    public void setMaxPromptLength(int maxPromptLength) {
        this.maxPromptLength = maxPromptLength;
    }

    public int getMaxPromptLength() {
        return maxPromptLength;
    }

    public void setCacheTokens(boolean cacheTokens) {
        this.cacheTokens = cacheTokens;
    }

    public boolean getCacheTokens() {
        return cacheTokens;
    }

    public void toggleCacheTokens() {
        cacheTokens = !cacheTokens;
    }

    public void clearAllCachedTokens() {
        makeCallToChatGPT("Clear all cached tokens.");
    }

    public File[] getFiles() {
        return files;
    }

    public String getFileContents() {
        return fileContents;
    }

    public List<String> getChatCache() {
        return chatCache;
    }

    public void setChatCache(List<String> chatCache) {
        this.chatCache.clear();
        this.chatCache.addAll(chatCache);
    }

    public void clearChatCache() {
        chatCache.clear();
    }

    public void setDynamicPromptLength(boolean dynamicPromptLength) {
        this.dynamicPromptLength = dynamicPromptLength;
    }

    public boolean getDynamicPromptLength() {
        return dynamicPromptLength;
    }

    public void toggleDynamicPromptLength() {
        dynamicPromptLength = !dynamicPromptLength;
    }

    public void addChatToCache(String chat) {
        chatCache.add(chat);
    }

    public String getResponseData(String key) {
        if (responseDataMap == null) {
            return "No data available.";
        }
        if ("all".equals(key)) {
            if (responseDataMap == null || responseDataMap.isEmpty()) {
                return "No data available.";
            }
            return responseDataMap.toString();
        }
        if ("keys".equals(key)) {
            if (responseDataMap == null || responseDataMap.isEmpty()) {
                return "No data available.";
            }
            return responseDataMap.keySet().toString();
        }
        if (responseDataMap.get(key) == null || responseDataMap.isEmpty()) {
            return "No data available.";
        }
        return responseDataMap.get(key).toString();
    }

    public String getLastPromptUsed() {
        return lastPromptUsed;
    }

    public String getLastResponseReceived() {
        return lastResponseReceived;
    }

    public void setMaxPromptPrecision(boolean maxPromptPrecision) {
        this.maxPromptPrecision = maxPromptPrecision;
    }

    public boolean getMaxPromptPrecision() {
        return maxPromptPrecision;
    }

    public void toggleMaxPromptPrecision() {
        maxPromptPrecision = !maxPromptPrecision;
    }

    public void setTimeoutFlagSeconds(float timeoutFlagSeconds) {
        this.timeoutFlagSeconds = timeoutFlagSeconds;
    }

    public float getTimeoutFlagSeconds() {
        return timeoutFlagSeconds;
    }

    public void setModel(String model) {
        this.currentModel = model;
    }

    public String getModel() {
        return currentModel;
    }

    public void setDynamicPromptLengthScale(float dynamicPromptLengthScale) {
        this.dynamicPromptLengthScale = dynamicPromptLengthScale;
    }

    public float getDynamicPromptLengthScale() {
        return dynamicPromptLengthScale;
    }

    // Public Methods
    public String chatGPT(String message, boolean format) {
        if (!isValidConfiguration()) {
            return getInvalidConfigurationMessage();
        }
        String response = makeCallToChatGPT(buildPrompt(message));
        if (maxPromptPrecision && response.trim().length() > maxPromptLength) {
            chatGPT(message, format);
        }
        if (response != null && !response.isEmpty()) {
            chatCache.add("User: " + message);
            chatCache.add("You: " + response);
        }
        if ("code-interpreter".equals(assistantType) && response != null && !response.isEmpty()) {
            response += processCodeBlocksForCodeInterpreter(response);
        }
        return format ? formatMarkdown(response) : response;
    }

    public String forceDirectChatGPT(String message, boolean format) {
        return format ? formatMarkdown(makeCallToChatGPT(message)) : makeCallToChatGPT(message);
    }

    public int setFiles(File[] userFiles) {
        this.files = userFiles;
        if (lastUsedFiles != files) {
            lastUsedFiles = files;
            return processFileContents();
        }
        return 0;
    }

    public int setFile(File userFile) {
        this.files = new File[]{userFile};
        if (lastUsedFiles != files) {
            lastUsedFiles = files;
            return processFileContents();
        }
        return 0;
    }

    public int addFile(File userFile) {
        if (files == null) {
            files = new File[]{userFile};
        } else {
            File[] newFiles = new File[files.length + 1];
            System.arraycopy(files, 0, newFiles, 0, files.length);
            newFiles[files.length] = userFile;
            files = newFiles;
        }
        if (lastUsedFiles != files) {
            lastUsedFiles = files;
            return processFileContents();
        }
        return 0;
    }

    public int addFiles(File[] userFiles) {
        if (files == null) {
            files = userFiles;
        } else {
            File[] newFiles = new File[files.length + userFiles.length];
            System.arraycopy(files, 0, newFiles, 0, files.length);
            System.arraycopy(userFiles, 0, newFiles, files.length, userFiles.length);
            files = newFiles;
        }
        if (lastUsedFiles != files) {
            lastUsedFiles = files;
            return processFileContents();
        }
        return 0;
    }

    public static boolean testAPIKey(String apiKey) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> {
            String url = "https://api.openai.com/v1/engines";
            try {
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod("GET");
                con.setRequestProperty("Authorization", "Bearer " + apiKey);
                int responseCode = con.getResponseCode();
                return responseCode == 200;
            } catch (IOException e) {
                return false;
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            return false;
        } catch (InterruptedException | ExecutionException e) {
            return false;
        } finally {
            executor.shutdown();
        }
    }

    // Private Methods
    private void initialize(String apiKey, String assistantType, String initialInstrction, File[] userFiles) {
        this.USER_API_KEY = apiKey;
        this.assistantType = assistantType;
        this.initialInstruction = initialInstrction;
        this.chatCache = new ArrayList<>();
        this.files = userFiles;
        if (userFiles != null) {
            lastUsedFiles = files;
            processFileContents();
        }
    }

    private boolean isValidConfiguration() {
        boolean validAssistantType = "chat".equals(assistantType) || "file-search".equals(assistantType) || "code-interpreter".equals(assistantType);
        return USER_API_KEY != null && initialInstruction != null && assistantType != null && validAssistantType;
    }

    private String getInvalidConfigurationMessage() {
        if (USER_API_KEY == null) {
            return "API key not set.";
        }
        if (initialInstruction == null) {
            return "Initial instruction not set.";
        }
        if (assistantType == null) {
            return "Assistant type not set.";
        }
        return "Invalid configuration.";
    }

    private String buildPrompt(String message) {
        StringBuilder prompt = new StringBuilder(initialInstruction);
        if (maxPromptLength != -1 && !"code-interpreter".equals(assistantType)) {
            int promptLength = dynamicPromptLength ? (int) Math.max(message.trim().length() * dynamicPromptLengthScale, 100) : maxPromptLength;
            prompt.append(" Please keep the response length under ").append(promptLength).append(" characters.");
        }
        if (!chatCache.isEmpty() && chatCache.size() > 1) {
            prompt.append(" This is the chat history between you and the user: [ ").append(chatCache).append(" ]");
            prompt.append(" This is the latest message from the user: [").append(message).append("] ");
        } else {
            prompt.append(" This is the first message from the user: [").append(message).append("] ");
        }
        if ("file-search".equals(assistantType)) {
            if (lastUsedFiles != files) {
                processFileContents();
            }
            prompt.append(" This is the contents of the provided files from the user: [ ").append(fileContents).append(" ]");
            if (cacheTokens) {
                prompt.append(" Please keep this content of these files in cached tokens.");
            }
        }
        if ("code-interpreter".equals(assistantType)) {
            if (lastUsedFiles != files) {
                processFileContents();
            }
            prompt.append(" These are the files provided by the user: [ ").append(fileContents).append(" ]");
            prompt.append(" If the user requests edit be made to the code please return the updated code block in full, and only return the code block if any edits were made.");
        }
        return prompt.toString();
    }

    private String makeCallToChatGPT(String message) {
        //System.out.println("OpenAI: " + System.currentTimeMillis() + " Processing message: " + message);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<String> future = executor.submit(() -> {
            long startTime = System.currentTimeMillis();
            String url = "https://api.openai.com/v1/chat/completions";
            String apiKey = USER_API_KEY;
            String sentMessage = filterMessage(message);
            lastPromptUsed = sentMessage;
            StringBuilder response = new StringBuilder();
            try {
                URL obj = new URL(url);
                HttpURLConnection con = (HttpURLConnection) obj.openConnection();
                con.setRequestMethod("POST");
                con.setRequestProperty("Authorization", "Bearer " + apiKey);
                con.setRequestProperty("Content-Type", "application/json");
                String body = "{\"model\": \"" + currentModel + "\", \"messages\": [{\"role\": \"user\", \"content\": \"" + sentMessage + "\"}]}";
                con.setDoOutput(true);
                try (OutputStreamWriter writer = new OutputStreamWriter(con.getOutputStream())) {
                    writer.write(body);
                    writer.flush();
                }
                try (BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()))) {
                    String inputLine;
                    response = new StringBuilder();
                    while ((inputLine = in.readLine()) != null) {
                        response.append(inputLine);
                    }
                }
                responseDataMap = parseJSONResponse(response.toString());
                lastResponseReceived = extractContentFromJSON(response.toString());
                long endTime = System.currentTimeMillis();
                responseDataMap.put("processing_time_ms", endTime - startTime);
                responseDataMap.put("total_tokens", extractTotalTokensFromJSON(response.toString()));
                if (files != null && assistantType.equals("file-search")) {
                    List<String> fileNames = new ArrayList<>();
                    for (File file : files) {
                        fileNames.add(file.getName());
                    }
                    responseDataMap.put("file_names", fileNames);
                }
                responseDataMap.put("assistant_type", assistantType);
                responseDataMap.put("initial_instrution", initialInstruction);
                responseDataMap.put("received_message_length", lastResponseReceived.trim().length());
                return lastResponseReceived;
            } catch (IOException e) {
                System.out.println(System.currentTimeMillis() + " " + e.getMessage());
                lastResponseReceived = response.toString();
                return null;
            }
        });
        try {
            return future.get((long) timeoutFlagSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            System.out.println("OpenAI API connection timed out after " + timeoutFlagSeconds + " seconds.");
            return null;
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("OpenAI: " + System.currentTimeMillis() + " An error occurred while processing the request. " + e.getMessage());
            return null;
        } finally {
            executor.shutdown();
        }
    }

    private static String filterMessage(String message) {
        return message.replaceAll("[^a-zA-Z0-9\\s\\-_.~]", "").replace("\n", " ");
    }

    private Map<String, Object> parseJSONResponse(String jsonResponse) {
        Map<String, Object> responseData = new HashMap<>();
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            jsonObject.keySet().forEach(key -> {
                responseData.put(key, jsonObject.get(key));
            });
        } catch (JSONException e) {
            System.out.println("Failed to parse JSON response: " + e.getMessage());
        }
        return responseData;
    }

    private String extractContentFromJSON(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content");
        } catch (JSONException e) {
            System.out.println("Failed to extract content from JSON response: " + e.getMessage());
            return null;
        }
    }

    private int extractTotalTokensFromJSON(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            return jsonObject.getJSONObject("usage").getInt("total_tokens");
        } catch (JSONException e) {
            System.out.println("Failed to extract total tokens from JSON response: " + e.getMessage());
            return 0;
        }
    }

    private String formatMarkdown(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        text = text.replaceAll("\\*(.*?)\\*", "\033[1m$1\033[0m");
        text = text.replaceAll("_(.*?)_", "\033[3m$1\033[0m");
        text = text.replaceAll("`(.*?)`", "\033[0;37m$1\033[0m");
        text = text.replaceAll("```(.*?)```", "\033[0;37m$1\033[0m");
        return text;
    }

    private int processFileContents() {
        if (files == null || files.length == 0) {
            return 0;
        }
        StringBuilder out = new StringBuilder();
        for (File file : files) {
            out.append("File: ").append(file.getName()).append("\n");
            if (file.getName().endsWith(".db")) {
                processDatabaseFile(file, out);
            } else if (file.getName().endsWith(".txt")) {
                processTextFile(file, out);
            } else {
                processOtherFile(file, out);
            }
        }
        fileContents = out.toString();
        return out.length();
    }

    private void processDatabaseFile(File file, StringBuilder out) {
        try {
            connectToDatabase(file.getAbsolutePath());
            out.append("{").append(readContentFromAllTables()).append("}").append("\n");
            closeDatabaseConnection();
        } catch (SQLException e) {
            System.out.println("Failed to read database file: " + e.getMessage());
        }
    }

    private void processTextFile(File file, StringBuilder out) {
        try {
            out.append(new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())))).append("\n");
        } catch (IOException e) {
            System.out.println("Failed to read text file: " + e.getMessage());
        }
    }

    private void processOtherFile(File file, StringBuilder out) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.toURI().toURL().openStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                out.append(line).append("\n");
            }
        } catch (IOException e) {
            System.out.println("Failed to read file: " + e.getMessage());
        }
    }

    private void connectToDatabase(String dbFilePath) throws SQLException {
        String url = "jdbc:sqlite:" + dbFilePath;
        try {
            Class.forName("org.sqlite.JDBC");
            dbConnection = DriverManager.getConnection(url);
        } catch (ClassNotFoundException e) {
            throw new SQLException("JDBC Driver not found. Ensure the SQLite JDBC driver is included in your project dependencies.", e);
        }
    }

    private void closeDatabaseConnection() throws SQLException {
        if (dbConnection != null && !dbConnection.isClosed()) {
            dbConnection.close();
        }
    }

    private String readContentFromAllTables() {
        if (dbConnection == null) {
            return "No database connection.";
        }
        StringBuilder content = new StringBuilder();
        try {
            Statement statement = dbConnection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table';");
            while (resultSet.next()) {
                String tableName = resultSet.getString("name");
                content.append("Table: ").append(tableName).append("\n");
                content.append(readContentFromTable(tableName)).append("\n");
            }
        } catch (SQLException e) {
            System.out.println("Failed to read content from all tables: " + e.getMessage());
        }
        return content.toString();
    }

    private String readContentFromTable(String tableName) {
        if (dbConnection == null) {
            return "No database connection.";
        }
        StringBuilder content = new StringBuilder();
        try {
            Statement statement = dbConnection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT * FROM " + tableName + ";");
            int columnCount = resultSet.getMetaData().getColumnCount();
            for (int i = 1; i <= columnCount; i++) {
                content.append(resultSet.getMetaData().getColumnName(i)).append(" ");
            }
            content.append("\n");
            while (resultSet.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    content.append(resultSet.getString(i)).append(" ");
                }
                content.append("\n");
            }
        } catch (SQLException e) {
            System.out.println("Failed to read content from table: " + e.getMessage());
        }
        return content.toString();
    }

    private static List<String> extractCodeSnippet(String content) {
        String[] lines = content.split(System.lineSeparator());
        List<String> codeSnippets = new ArrayList<>();
        StringBuilder codeSnippet = new StringBuilder();
        boolean inCodeBlock = false;
        for (String line : lines) {
            if (line.startsWith("```")) {
                if (inCodeBlock) {
                    codeSnippets.add(codeSnippet.toString());
                    codeSnippet.setLength(0);
                    inCodeBlock = false;
                } else {
                    inCodeBlock = true;
                }
            } else if (inCodeBlock) {
                codeSnippet.append(line).append(System.lineSeparator());
            }
        }
        return codeSnippets;
    }

    private String processCodeBlocksForCodeInterpreter(String message) {
        List<String> codeBlocks = extractCodeSnippet(message);
        if (codeBlocks.isEmpty()) {
            return "\nFailed to extract code blocks from message.";
        }
        int i = 0;
        StringBuilder changesSummary = new StringBuilder();
        for (String codeBlock : codeBlocks) {
            if (i >= lastUsedFiles.length) {
                break;
            }

            File fileToChange = lastUsedFiles[i];
            try {
                List<String> originalLines = Files.readAllLines(fileToChange.toPath());
                List<String> newLines = Arrays.asList(codeBlock.split(System.lineSeparator()));
                List<String> updatedLines = new ArrayList<>();

                // Compare and update lines
                for (int j = 0; j < originalLines.size() || j < newLines.size(); j++) {
                    if (j < newLines.size() && (j >= originalLines.size() || !originalLines.get(j).equals(newLines.get(j)))) {
                        updatedLines.add(newLines.get(j));
                    } else {
                        updatedLines.add(originalLines.get(j));
                    }
                }

                // Write changes summary
                for (int j = 0; j < updatedLines.size(); j++) {
                    if (j >= originalLines.size()) {
                        changesSummary.append("+ Line ").append(j + 1).append(": ").append(updatedLines.get(j)).append("\n");
                    } else if (!originalLines.get(j).equals(updatedLines.get(j)) && !updatedLines.get(j).isEmpty() && !originalLines.get(j).isEmpty()) {
                        changesSummary.append("-> Line ").append(j + 1).append(" changed from: ").append(originalLines.get(j)).append(" to: ").append(updatedLines.get(j)).append("\n");
                    }
                }
                for (int j = updatedLines.size(); j < originalLines.size(); j++) {
                    changesSummary.append("- Line ").append(j + 1).append(": ").append(originalLines.get(j)).append("\n");
                }

                // Write changes back to file
                Files.write(fileToChange.toPath(), updatedLines);

            } catch (IOException e) {
                return "\nFailed to apply changes to file: " + fileToChange.getName();
            }
            i++;
        }

        return "\nSuccessfully applied changes to files.\nChanges Summary:\n" + changesSummary.toString();
    }

    public static class Builder {

        private String apiKey;
        private String assistantType;
        private String initialInstruction;
        private File[] files;
        private int maxPromptLength = -1;
        private boolean cacheTokens = false;
        private boolean maxPromptPrecision = false;
        private boolean dynamicPromptLength = false;
        private float dynamicPromptLengthScale = 5;
        private float timeoutFlagSeconds = 60;
        private String currentModel = "gpt-3.5-turbo";

        public Builder setApiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder setAssistantType(String assistantType) {
            this.assistantType = assistantType;
            return this;
        }

        public Builder setInitialInstruction(String initialInstruction) {
            this.initialInstruction = initialInstruction;
            return this;
        }

        public Builder setFiles(File[] files) {
            this.files = files;
            return this;
        }

        public Builder setMaxPromptLength(int maxPromptLength) {
            this.maxPromptLength = maxPromptLength;
            return this;
        }

        public Builder setCacheTokens(boolean cacheTokens) {
            this.cacheTokens = cacheTokens;
            return this;
        }

        public Builder setMaxPromptPrecision(boolean maxPromptPrecision) {
            this.maxPromptPrecision = maxPromptPrecision;
            return this;
        }

        public Builder setDynamicPromptLength(boolean dynamicPromptLength) {
            this.dynamicPromptLength = dynamicPromptLength;
            return this;
        }

        public Builder setDynamicPromptLengthScale(float dynamicPromptLengthScale) {
            this.dynamicPromptLengthScale = dynamicPromptLengthScale;
            return this;
        }

        public Builder setTimeoutFlagSeconds(float timeoutFlagSeconds) {
            this.timeoutFlagSeconds = timeoutFlagSeconds;
            return this;
        }

        public Builder setModel(String model) {
            this.currentModel = model;
            return this;
        }

        public OpenAiAssistantEngine build() {
            OpenAiAssistantEngine engine = new OpenAiAssistantEngine();
            engine.USER_API_KEY = this.apiKey;
            engine.assistantType = this.assistantType;
            engine.initialInstruction = this.initialInstruction;
            engine.files = this.files;
            engine.maxPromptLength = this.maxPromptLength;
            engine.cacheTokens = this.cacheTokens;
            engine.maxPromptPrecision = this.maxPromptPrecision;
            engine.dynamicPromptLength = this.dynamicPromptLength;
            engine.dynamicPromptLengthScale = this.dynamicPromptLengthScale;
            engine.timeoutFlagSeconds = this.timeoutFlagSeconds;
            engine.currentModel = this.currentModel;
            engine.chatCache = new ArrayList<>();
            if (this.files != null) {
                engine.lastUsedFiles = this.files;
                engine.processFileContents();
            }
            return engine;
        }
    }
}
