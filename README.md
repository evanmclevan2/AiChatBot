# OpenAiAssistantEngine

The `OpenAiAssistantEngine` class is designed to interact with OpenAI's GPT models, providing functionalities for both chat-based and file-search-based assistants. It supports various configurations and can handle different file types, including text and database files.

## Features

- Supports multiple GPT models.
- Configurable API key, initial instructions, and assistant type.
- Handles chat history and file contents.
- Dynamic prompt length and caching options.
- Database file processing.

## Installation

Ensure you have the necessary dependencies in your project, including the SQLite JDBC driver for database file processing.

## Usage

### Initialization

You can initialize the `OpenAiAssistantEngine` in several ways:

```java
// Using API key, assistant type, and initial instruction
OpenAiAssistantEngine engine = new OpenAiAssistantEngine("your-api-key", "chat", "initial instruction");

// Using API key, assistant type, initial instruction, and user files
File[] files = {new File("path/to/file1.txt"), new File("path/to/file2.db")};
OpenAiAssistantEngine engine = new OpenAiAssistantEngine("your-api-key", "file-search", "initial instruction", files);

// Using a JSON string
String jsonString = "{\"apikey\": \"your-api-key\", \"assistantType\": \"chat\", \"instruction\": \"initial instruction\"}";
OpenAiAssistantEngine engine = new OpenAiAssistantEngine(jsonString);

// Using a JSONObject
JSONObject jsonConfig = new JSONObject();
jsonConfig.put("apikey", "your-api-key");
jsonConfig.put("assistantType", "chat");
jsonConfig.put("instruction", "initial instruction");
OpenAiAssistantEngine engine = new OpenAiAssistantEngine(jsonConfig);
```

### Configuration

You can set various configurations using the provided setters:

```java
engine.setAPIKey("your-api-key");
engine.setInititalInstruction("new instruction");
engine.setAssistantType("file-search");
engine.setMaxPromptLength(500);
engine.setCacheTokens(true);
engine.setDynamicPromptLength(true);
engine.setDynamicPromptLengthScale(3.0f);
engine.setTimeoutFlagSeconds(120);
engine.setModel("gpt-4.5-preview");
```

### Chatting with GPT

To send a message to the GPT model:

```java
String response = engine.chatGPT("Hello, how are you?", true);
System.out.println(response);
```

### Managing Files

You can set, add, or retrieve files:

```java
File[] files = {new File("path/to/file1.txt"), new File("path/to/file2.db")};
engine.setFiles(files);

File newFile = new File("path/to/newFile.txt");
engine.addFile(newFile);

File[] currentFiles = engine.getFiles();
String fileContents = engine.getFileContents();
```

### Testing API Key

To test if an API key is valid:

```java
boolean isValid = OpenAiAssistantEngine.testAPIKey("your-api-key");
System.out.println("API Key valid: " + isValid);
```

## License

This project is licensed under the MIT License.