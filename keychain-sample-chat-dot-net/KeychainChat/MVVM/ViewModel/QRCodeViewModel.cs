using System.Drawing;
using System.Windows.Input;
using System.Windows.Media;
using GalaSoft.MvvmLight.Command;
using KeychainChat.Interfaces;
using KeychainChat.MVVM.Model;
using KeychainChat.MVVM.Model.Personas;
using KeychainChat.Services;
using KeychainChat.Services.Database;
using NLog;

namespace KeychainChat.MVVM.ViewModel
{
    public class QRCodeViewModel : DialogViewModelBase<object>
    {
        private static string TAG = "QRCodseViewModel";

        private Logger log = LogManager.GetCurrentClassLogger();

        public DrawingImage QRCodeImage { get; private set; }

        public string PersonaUri { get; private set; }

        private KeychainService keychainService = KeychainService.GetInstance();

        public QRCodeViewModel(AccountQRCodeData qrCodeData)
        {
            QRCodeImage = qrCodeData.GetQRCode();
            PersonaUri = qrCodeData.id;
        }
    }
}
