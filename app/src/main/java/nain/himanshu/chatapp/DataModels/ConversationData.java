package nain.himanshu.chatapp.DataModels;

public class ConversationData {

    private String conversationId, otherName, otherProfilePic, latestMessage;

    public ConversationData() {
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getOtherName() {
        return otherName;
    }

    public void setOtherName(String otherName) {
        this.otherName = otherName;
    }

    public String getOtherProfilePic() {
        return otherProfilePic;
    }

    public void setOtherProfilePic(String otherProfilePic) {
        this.otherProfilePic = otherProfilePic;
    }

    public String getLatestMessage() {
        return latestMessage;
    }

    public void setLatestMessage(String latestMessage) {
        this.latestMessage = latestMessage;
    }
}
