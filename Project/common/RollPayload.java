package Project.Common;

// /*
public class RollPayload extends Payload {
    private int quantity;
    private int sides;
    private boolean isSimpleRoll;

    public RollPayload() {
        setPayloadType(PayloadType.ROLL);
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getSides() {
        return sides;
    }

    public void setSides(int sides) {
        this.sides = sides;
    }

    public boolean isSimpleRoll() {
        return isSimpleRoll;
    }

    public void setSimpleRoll(boolean isSimpleRoll) {
        this.isSimpleRoll = isSimpleRoll;
    }

    @Override
    public String toString() {
        return String.format("RollPayload[%s] Client Id [%s] Quantity: [%d] Sides: [%d] Simple: [%b]",
                getPayloadType(), getClientId(), quantity, sides, isSimpleRoll);
    }
}
// */