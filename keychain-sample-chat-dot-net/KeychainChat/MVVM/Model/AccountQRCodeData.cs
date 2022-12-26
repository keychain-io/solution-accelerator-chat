using Newtonsoft.Json;
using QRCoder;
using QRCoder.Xaml;
using System.Drawing;
using System.Windows.Media;

namespace KeychainChat.MVVM.Model
{
    public class AccountQRCodeData
    {
        public string id;
        public string firstName;
        public string lastName;

        public DrawingImage GetQRCode()
        {
            var json = JsonConvert.SerializeObject(this);
            var generator = new QRCodeGenerator();
            var data = generator.CreateQrCode(json, QRCodeGenerator.ECCLevel.Q);
            var qrCode = new XamlQRCode(data);

            return qrCode.GetGraphic(5);
        }
    }
}
