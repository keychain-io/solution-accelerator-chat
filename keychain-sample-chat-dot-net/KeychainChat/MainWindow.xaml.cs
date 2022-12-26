using GalaSoft.MvvmLight;
using KeychainChat.MVVM.ViewModel;
using System;
using System.Windows;
using System.Windows.Input;

namespace KeychainChat
{
    /// <summary>
    /// Interaction logic for MainWindow.xaml
    /// </summary>
    public partial class MainWindow : Window
    {
        public static readonly DependencyProperty MaxMessageWidthProperty =
            DependencyProperty.Register(
                name: "MaxMessageWidth",
                propertyType: typeof(double),
                ownerType: typeof(MainWindow),
                typeMetadata: new FrameworkPropertyMetadata(defaultValue: 400.0));

        public double MaxMessageWidth {
            get => (double)GetValue(MaxMessageWidthProperty);
            set => SetValue(MaxMessageWidthProperty, value);
        }

        public MainWindow()
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

        private void MinimizeButton_Click(object sender, RoutedEventArgs e)
        {
            Application.Current.MainWindow.WindowState = WindowState.Minimized;
        }

        private void MaximizeButton_Click(object sender, RoutedEventArgs e)
        {
            if (Application.Current.MainWindow.WindowState != WindowState.Maximized)
            {
                Application.Current.MainWindow.WindowState = WindowState.Maximized;
            }
            else
            {
                Application.Current.MainWindow.WindowState = WindowState.Normal;
            }
        }

        private void ExitButton_Click(object sender, RoutedEventArgs e)
        {
            Environment.Exit(0);
        }

        private void Window_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            MaxMessageWidth = ChatListView.ActualWidth - 100;
        }
    }
}
