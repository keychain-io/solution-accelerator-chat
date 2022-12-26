using System;
using System.Diagnostics;
using Keychain;
using KeychainChat.MVVM.Model;
using KeychainChat.Utils;
using NLog;

namespace KeychainChat.Services.Channel
{
    public class ChannelMessage {
    private static string TAG = "ChannelMessage";
        public static string ERROR_CREATING_PAIR_RESPONSE = "Error creating pair response: ";
        public static string ERROR_CREATING_PAIR_REQUEST = "Error creating pair request: ";

        private static Logger log = LogManager.GetCurrentClassLogger();

        public static PairingMessage? MakePairAck(Persona me, PairingMessage respondTo)
        {
            try
            {
                if (respondTo.msgType != MessageType.PairResponse)
                {
                    return null;
                }

                PairingMessage resp = new PairingMessage
                {
                    msgType = MessageType.PairAck,
                    receiverId = respondTo.senderId,
                    senderId = StringUtils.GetUriString(me.getUri()),
                    senderName = me.getName(),
                    senderSubName = me.getSubName()
                };

                return resp;
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_CREATING_PAIR_RESPONSE, e);
            }

            return null;
        }

        public static PairingMessage? MakePairResponse(Persona me, PairingMessage respondTo)
        {
            try
            {
                if (respondTo.msgType != MessageType.PairRequest)
                {
                    return null;
                }

                PairingMessage resp = new PairingMessage
                {
                    msgType = MessageType.PairResponse,
                    receiverId = respondTo.senderId,
                    senderId = StringUtils.GetUriString(me.getUri()),
                    senderName = me.getName(),
                    senderSubName = me.getSubName()
                };

                return resp;
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_CREATING_PAIR_RESPONSE, e);
            }

            return null;
        }

        public static PairingMessage? MakePairRequest(Persona me, string requestUri)
        {

            try
            {
                PairingMessage request = new PairingMessage
                {
                    msgType = MessageType.PairRequest,
                    receiverId = requestUri,
                    senderId = StringUtils.GetUriString(me.getUri()),
                    senderName = me.getName(),
                    senderSubName = me.getSubName()
                };

                return request;
            }
            catch (Exception e)
            {
                Debug.WriteLine(ERROR_CREATING_PAIR_REQUEST, e);
            }

            return null;
        }
    }
}
