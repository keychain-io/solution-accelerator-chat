namespace KeychainChat.Services
{
    public class DirectoryEntry
    {
        public string name { get; set; }
        public string encr_txid { get; set; }
        public string encr_vout { get; set; }
        public string sign_txid { get; set; }
        public string sign_vout { get; set; }
    }
}
