using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using KeychainChat.Interfaces;

namespace KeychainChat.MVVM.View
{
    /// <summary>
    /// Interaction logic for CreatePersonaWindow.xaml
    /// </summary>
    public partial class CreatePersonaWindow : Window, IDialogWindow
    {
        public CreatePersonaWindow()
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

        private async void SaveButton_IsEnabledChanged(object sender, DependencyPropertyChangedEventArgs e)
        {
            if ((bool)e.NewValue)
            {
                await Task.Run(async () =>
                {
                    await Task.Delay(200);
                    SaveButton.Dispatcher.BeginInvoke(new Action(() => ((Button)sender).Focus()));
                });
            }
        }
    }
}
