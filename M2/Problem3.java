// Shahriar Topu - st278 - 06/06/2024

package M2;

import java.util.Arrays;

public class Problem3 {
    public static void main(String[] args) {
        Integer[] a1 = new Integer[]{-1, -2, -3, -4, -5, -6, -7, -8, -9, -10};
        Integer[] a2 = new Integer[]{-1, 1, -2, 2, 3, -3, -4, 5};
        Double[] a3 = new Double[]{-0.01, -0.0001, -.15};
        String[] a4 = new String[]{"-1", "2", "-3", "4", "-5", "5", "-6", "6", "-7", "7"};
        
        bePositive(a1);
        bePositive(a2);
        bePositive(a3);
        bePositive(a4);
    }

    static <T> void bePositive(T[] arr){
        System.out.println("Processing Array:" + Arrays.toString(arr));
        Object[] output = new Object[arr.length];
        
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] instanceof Integer) {
                output[i] = Math.abs((Integer) arr[i]);
            } else if (arr[i] instanceof Double) {
                output[i] = Math.abs((Double) arr[i]);
            } else if (arr[i] instanceof String) {
                int num = Integer.parseInt((String) arr[i]);
                output[i] = Integer.toString(Math.abs(num));
            }
        }

        StringBuilder sb = new StringBuilder();
        for (Object i : output) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(String.format("%s (%s)", i, i.getClass().getSimpleName().substring(0, 1)));
        }
        System.out.println("Result: " + sb.toString());
    }
}
