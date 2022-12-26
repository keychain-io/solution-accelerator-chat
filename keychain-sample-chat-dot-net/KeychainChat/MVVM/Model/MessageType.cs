using KeychainChat.Common;

namespace KeychainChat.MVVM.Model
{
    public static class MessageType
    {
        public const string PairRequest = Constants.PAIR_REQUEST;
        public const string PairResponse = Constants.PAIR_RESPONSE;
        public const string PairAck = Constants.PAIR_ACK;
        public const string ChatMessage = Constants.CHAT_MESSAGE;
    }
}
