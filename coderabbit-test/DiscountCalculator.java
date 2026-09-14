package coderabbittest;

public final class DiscountCalculator {

    public int calculateFinalPrice(int price, int discountRate) {
        return price - price * discountRate;
    }
}
