package io.healthresetplan.modules.sms;

public class SendSmsResult {

    private final boolean sent;
    private final String provider;
    private final String sequenceNumber;

    public SendSmsResult(boolean sent, String provider, String sequenceNumber) {
        this.sent = sent;
        this.provider = provider;
        this.sequenceNumber = sequenceNumber;
    }

    public boolean isSent() { return sent; }
    public String getProvider() { return provider; }
    public String getSequenceNumber() { return sequenceNumber; }
}
