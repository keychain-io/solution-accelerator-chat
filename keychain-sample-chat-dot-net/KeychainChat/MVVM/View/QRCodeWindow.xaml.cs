using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using KeychainChat.Interfaces;

namespace KeychainChat.MVVM.View
{
    /// <summary>
    /// Interaction logic for QRCodeWindow.xaml
    /// </summary>
    public partial class QRCodeWindow : Window, IDialogWindow
    {
        public QRCodeWindow()
        {
            InitializeComponent();
        }

        private void Border_MouseDown(object sender, MouseButtonEventArgs e)
        {
            if (e.LeftButton == MouseButtonState.Pressed)
            {
                DragMove();
            }
        }
    }
}
