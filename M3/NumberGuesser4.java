package M3;
// st278 and 06-11-2024
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.Scanner;

public class NumberGuesser4 {
    private int maxLevel = 1;
    private int level = 1;
    private int strikes = 0;
    private int maxStrikes = 5;
    private int number = -1;
    private boolean pickNewRandom = true;
    private Random random = new Random();
    private String fileName = "ng4.txt";
    private String[] fileHeaders = { "Level", "Strikes", "Number", "MaxLevel", "MaxStrikes" };

    private void saveState() {
        String[] data = { level + "", strikes + "", number + "", maxLevel + "", maxStrikes + "" };
        String output = String.join(",", data);
        try (FileWriter fw = new FileWriter(fileName)) {
            fw.write(String.join(",", fileHeaders));
            fw.write("\n");
            fw.write(output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadState() {
        File file = new File(fileName);
        if (!file.exists()) {
            return;
        }
        try (Scanner reader = new Scanner(file)) {
            int lineNumber = 0;
            while (reader.hasNextLine()) {
                String text = reader.nextLine();
                if (lineNumber == 1) {
                    String[] data = text.split(",");
                    String level = data[0];
                    String strikes = data[1];
                    String number = data[2];
                    String maxLevel = data[3];
                    String maxStrikes = data[4];
                    int temp = strToNum(level);
                    if (temp > -1) {
                        this.level = temp;
                    }
                    temp = strToNum(strikes);
                    if (temp > -1) {
                        this.strikes = temp;
                    }
                    temp = strToNum(number);
                    if (temp > -1) {
                        this.number = temp;
                        pickNewRandom = false;
                    }
                    temp = strToNum(maxLevel);
                    if (temp > -1) {
                        this.maxLevel = temp;
                    }
                    temp = strToNum(maxStrikes);
                    if (temp > -1) {
                        this.maxStrikes = temp;
                    }
                }
                lineNumber++;
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        System.out.println("Loaded state");
        int range = 10 + ((level - 1) * 5);
        System.out.println("Welcome to level " + level);
        System.out.println(
                "I picked a random number between 1-" + (range) + ", let's see if you can guess.");
    }

    private void generateNewNumber(int level) {
        int range = 10 + ((level - 1) * 5);
        System.out.println("Welcome to level " + level);
        System.out.println(
                "I picked a random number between 1-" + (range) + ", let's see if you can guess.");
        number = random.nextInt(range) + 1;
    }

    private void win() {
        System.out.println("That's right!");
        level++;
        strikes = 0;
    }

    private boolean processCommands(String message) {
        boolean processed = false;
        if (message.equalsIgnoreCase("quit")) {
            System.out.println("Tired of playing? No problem, see you next time.");
            processed = true;
        }
        return processed;
    }

    private void lose() {
        System.out.println("Uh oh, looks like you need to get some more practice.");
        System.out.println("The correct number was " + number);
        strikes = 0;
        level--;
        if (level < 1) {
            level = 1;
        }
    }
// st278 and 06-11-2024
    private void processGuess(int guess) {
        if (guess < 0) {
            return;
        }
        System.out.println("You guessed " + guess);
        if (guess == number) {
            win();
            pickNewRandom = true;
        } else {
            System.out.println("That's wrong");
            if (guess < number) {
                System.out.println("Hint: Try a higher number");
            } else {
                System.out.println("Hint: Try a lower number");
            }
            strikes++;
            if (strikes >= maxStrikes) {
                lose();
                pickNewRandom = true;
            }
        }
        saveState();
    }

    private int strToNum(String message) {
        int guess = -1;
        try {
            guess = Integer.parseInt(message.trim());
        } catch (NumberFormatException e) {
            System.out.println("You didn't enter a number, please try again");
        } catch (Exception e2) {
            System.out.println("Null message received");
        }
        return guess;
    }

    // st278 and 06-11-2024
    private void selectDifficulty(Scanner input) {
        System.out.println("Select difficulty: easy, medium, hard");
        String difficulty = input.nextLine().toLowerCase();
        switch (difficulty) {
            case "easy":
                maxStrikes = 10;
                break;
            case "medium":
                maxStrikes = 5;
                break;
            case "hard":
                maxStrikes = 3;
                break;
            default:
                System.out.println("Invalid selection, defaulting to medium difficulty.");
                maxStrikes = 5;
                break;
        }
        System.out.println("Difficulty set to " + difficulty + ". Max strikes: " + maxStrikes);
    }

    public void start() {
        Scanner input = new Scanner(System.in);
        try {
            System.out.println("Welcome to NumberGuesser4.0");
            System.out.println("To exit, type the word 'quit'.");
            loadState();
            selectDifficulty(input);
            do {
                if (pickNewRandom) {
                    generateNewNumber(level);
                    saveState();
                    pickNewRandom = false;
                }
                System.out.println("Type a number and press enter");
                if (input.hasNextLine()) {
                    String message = input.nextLine();
                    if (processCommands(message)) {
                        break;
                    }
                    int guess = strToNum(message);
                    processGuess(guess);
                } else {
                    System.out.println("No input received.");
                    break;
                }
            } while (true);
        } catch (Exception e) {
            System.out.println("An unexpected error occurred. Goodbye.");
            e.printStackTrace();
            System.out.println(e.getMessage());
        } finally {
            input.close();  // Ensure the scanner is closed to release the resource
        }
        System.out.println("Thanks for playing!");
    }

    public static void main(String[] args) {
        NumberGuesser4 ng = new NumberGuesser4();
        ng.start();
    }
}