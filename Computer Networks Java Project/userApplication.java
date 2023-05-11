import ithakimodem.Modem;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.io.FileWriter;

public class userApplication {
    private final Modem modem;

    public userApplication() {
        modem = new Modem();
        modem.setSpeed(100000);
        modem.setTimeout(2000);
        modem.open("ithaki");
    }

    public static void main(String[] args) throws IOException {

        String echoCode = "E8831\r";
        String imageErrorFreeCode = "M7442\r";
        String imageErrorCode = "G3400\r";
        String gpsCode = "P1093";
        String ackCode = "Q3352\r";
        String nackCode = "R9157\r";

        userApplication userApp = new userApplication();
        userApp.readWelcomeMessage();
        userApp.echoRequest(echoCode);
        userApp.getImage(imageErrorFreeCode, "image");
        userApp.getImage(imageErrorCode, "image1");
        userApp.getGpsImage(gpsCode);
        userApp.arqPackets(ackCode, nackCode);
    }

    public void readWelcomeMessage() {
        boolean serverMessageCompleted = false;
        String message = "";

        for (;;) {
            try {
                if (!serverMessageCompleted) {
                    int k;
                    k = modem.read();
                    char c = (char) k;
                    System.out.print(c);
                    message += c;

                    if (message.contains("connection tested.\r\n\n\n")) {
                        serverMessageCompleted = true;
                    }
                } else break;
            } catch (Exception x) {
                x.printStackTrace();
                break;
            }
        }
    }

    public void echoRequest(String code) throws IOException {
        long endTime = System.currentTimeMillis() + (4 * 60 * 1000);
        String echoPath = "/Users/tents/Developer/Java/echoData.txt";
        FileWriter writer = new FileWriter(echoPath);
        String formattedData = "";
        long startPacketTime;
        long endPacketTime;
        String message = "";
        int k;

        for (;;) {
            try {
                if (System.currentTimeMillis() >= endTime) {
                    break;
                }

                startPacketTime = System.currentTimeMillis();
                modem.write(code.getBytes());

                for (;;) {
                    k = modem.read();
                    if (k == -1) break;
                    char c = (char) k;
                    message += c;
                    System.out.print(c);

                    if (message.contains("PSTOP")) {
                        message = "";
                        endPacketTime = System.currentTimeMillis();
                        long responsePacketTime = endPacketTime - startPacketTime;
                        System.out.println(" - Response time: " + responsePacketTime + "ms");
                        formattedData += String.valueOf(responsePacketTime) + "\n";
                        break;
                    }
                }

            } catch (Exception x) {
                x.printStackTrace();
                break;
            }
        }
        writer.write(formattedData);
        writer.close();
    }

    public void getImage(String code, String imageName) {
        ArrayList<Byte> imageBytes = new ArrayList<>();
        int b = 0;
        int previousByte;
        boolean delimitersFound = false;

        modem.write(code.getBytes());
        for (;;) {
            try {

                previousByte = b;
                b = modem.read();
                if (b == -1) {
                    break;
                }
                if (!delimitersFound) {
                    //byte nextByte = (byte) modem.read();
                    if (previousByte == 0xFF && b == 0xD8) {
                        delimitersFound = true;
                        imageBytes.add((byte) 0xFF);
                        System.out.println("Byte 0xFF added");
                        imageBytes.add((byte) 0xD8);
                        System.out.println("Byte 0xD8 added");
                    }
                } else {
                    imageBytes.add((byte)b);
                    if (previousByte == 0xFF && b == 0xD9) {
                        imageBytes.add((byte) 0xFF);
                        System.out.println("Byte 0xFF added");
                        imageBytes.add((byte) 0xD9);
                        System.out.println("Byte 0xD9 added");
                        break;
                    }
                }


            } catch (Exception x) {
                x.printStackTrace();
                break;
            }
        }
        byte[] image = new byte[imageBytes.size()];
        for (int i = 0; i < imageBytes.size(); i++) {
            image[i] = imageBytes.get(i);
        }

        String imagePath = "/Users/tents/Developer/Java/" + imageName + ".jpg" ;
        try (FileOutputStream fos = new FileOutputStream(imagePath)) {
            fos.write(image);
            fos.flush();
            System.out.println("Image saved to: " + imagePath);
        } catch (Exception e) {
            System.out.println("Failed to write image to file: " + e.getMessage());
        }
    }

    public void getGpsImage(String code) {
        ArrayList<Double> latitudeList = new ArrayList<>();
        ArrayList<Double> longitudeList = new ArrayList<>();

        String message = "";
        int k;
        int counter = 0;

        String code1 = code + "R=1010080\r";
        modem.write(code1.getBytes());

        for (;;) {
            try {

                k = modem.read();
                char c = (char) k;

                if (c == '$') {
                    counter++;
                    System.out.print(message);
                    String[] messageParts = message.split(",");
                    if (messageParts[0].equals("$GPGGA")) {
                        latitudeList.add(Double.parseDouble(messageParts[2]));
                        longitudeList.add(Double.parseDouble(messageParts[4]));
                        System.out.println("Latitude: " + latitudeList.get(latitudeList.size()-1) + ", Longitude: " + longitudeList.get(longitudeList.size()-1));
                    }
                    message = "";
                }
                message += c;
                if (k == -1) break;

            } catch (Exception x) {
                x.printStackTrace();
                break;
            }
            if (counter==51) {
                System.out.println("STOP detected, breaking the loop...");
                break;
            }
        }

        String parameterT = "";
        for (int i = 0; i < latitudeList.size(); i += 8) {
            latitudeList.set(i, multiplyDecimalPart(latitudeList.get(i)) * 100);
            longitudeList.set(i, multiplyDecimalPart(longitudeList.get(i)) * 100);
            int x = latitudeList.get(i).intValue();
            int y = longitudeList.get(i).intValue();
            parameterT += "T=" + y + x;
        }
        code += parameterT + "\r";
        getImage(code, "image2");
    }

    public void arqPackets(String ackCode, String nackCode) throws IOException {
        long endTime = System.currentTimeMillis() + (4 * 60 * 1000);
        String arqPath = "/Users/tents/Developer/Java/arqData.txt";
        FileWriter writer = new FileWriter(arqPath);
        String formattedData = "";
        for (;;) {
            try {
                int k = 0;
                char prevCh;
                String message = "";
                String packet = "";
                int xor = 0;
                boolean stringFound = false;
                boolean fcsFound = false;
                String fcs = "";
                String ack = "";
                long startPacketTime = System.currentTimeMillis();
                long endPacketTime;

                if (System.currentTimeMillis() >= endTime) {
                    break;
                }

                modem.write(ackCode.getBytes());

                for (;;) {
                    prevCh = (char) k;
                    k = modem.read();
                    if (k == -1) break;
                    char c = (char) k;
                    packet += c;

                    if (prevCh == '<') {
                        message = "";
                        xor = 0;
                        stringFound = true;
                    }

                    if (c == '>') {
                        stringFound = false;
                    }

                    if (stringFound) {
                        message += c;
                        xor = xor ^ (int) c;
                    }

                    if (fcsFound && fcs.length() != 3) {
                        fcs += c;
                    }

                    if (prevCh == '>' && c == ' ') {
                        fcs = "";
                        fcsFound = true;
                    }

                    if (fcs.length() == 3) {
                        if (Integer.parseInt(fcs) == xor) {
                            ack = "ACK";
                        } else {
                            ack = "NACK";
                        }
                    }

                    if (packet.contains("PSTOP")) {
                        endPacketTime = System.currentTimeMillis();
                        long responsePacketTime = endPacketTime - startPacketTime;
                        System.out.print(c);
                        System.out.println(" - Message: " + message + " - ARQ: " + ack + " - Response time: " + responsePacketTime + "ms");
                        formattedData += String.valueOf(responsePacketTime) + "\n";
                        if (ack.equals("NACK")) {
                            modem.write(nackCode.getBytes());
                            packet = "";
                        } else break;
                    } else {
                        System.out.print(c);
                    }
                }

            } catch (Exception x) {
                x.printStackTrace();
                break;
            }
        }
        writer.write(formattedData);
        writer.close();
    }


    public double multiplyDecimalPart(double coordinate) {
        double decimalPart = coordinate % 1;
        return (int) coordinate + decimalPart * 0.6;
    }

}