package Project.common;

public class RollPayload extends Payload {
    private String rollType; // "single" or "multiple"
    private int max; // For single roll
    private int numDice; // For multiple roll
    private int sides; // For multiple roll
    private int result;

    public RollPayload() {
        setPayloadType(PayloadType.ROLL_COMMAND);
    }

    // Getters and setters
    public String getRollType() {
        return rollType;
    }

    public void setRollType(String rollType) {
        this.rollType = rollType;
    }

    public int getMax() {
        return max;
    }

    public void setMax(int max) {
        this.max = max;
    }

    public int getNumDice() {
        return numDice;
    }

    public void setNumDice(int numDice) {
        this.numDice = numDice;
    }

    public int getSides() {
        return sides;
    }

    public void setSides(int sides) {
        this.sides = sides;
    }

    public int getResult() {
        return result;
    }

    public void setResult(int result) {
        this.result = result;
    }
}