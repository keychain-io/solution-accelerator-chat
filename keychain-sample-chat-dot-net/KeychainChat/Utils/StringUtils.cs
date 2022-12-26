using System;
using System.Text;
using Uri = Keychain.Uri;

namespace KeychainChat.Utils
{
    public static class StringUtils
    {
        public static string? GetUriString(Uri uri)
        {
            if (uri == null)
            {
                return null;
            }

            var uriString = Encoding.UTF8.GetString(uri.serialize()).Replace(":1\0\0", ":1");

            return uriString.Length > 0 ? uriString : null;
        }

        public static string FromBase64Encoding(string base64String)
        {
            return Encoding.UTF8.GetString(Convert.FromBase64String(base64String));
        }

        public static string ToBase64Encoding(string data)
        {
            return Convert.ToBase64String(Encoding.UTF8.GetBytes(data));
        }

        public static string GetUTF8String(string inString)
        {
            var bytes = Encoding.Convert(
                Encoding.Unicode,
                Encoding.UTF8,
                Encoding.Unicode.GetBytes(inString));

            return Encoding.UTF8.GetString(bytes);
        }
    }
}
