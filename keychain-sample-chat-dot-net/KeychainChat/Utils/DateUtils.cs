﻿using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace KeychainChat.Utils
{
    public static class DateUtils
    {
        /// <summary>
        /// Converts a given DateTime into a Unix timestamp
        /// </summary>
        /// <param name="value">Any DateTime</param>
        /// <returns>The given DateTime in Unix timestamp format</returns>
        public static long ToUnixTimestamp(DateTime value)
        {
            return (long)(value.ToUniversalTime().Subtract(new DateTime(1970, 1, 1))).TotalSeconds;
        }

        /// <summary>
        /// Gets a Unix timestamp representing the current moment
        /// </summary>
        /// <param name="ignored">Parameter ignored</param>
        /// <returns>Now expressed as a Unix timestamp</returns>
        public static long UnixTimestamp()
        {
            return (long)(DateTime.UtcNow.Subtract(new DateTime(1970, 1, 1))).TotalSeconds;
        }

        /// <summary>
        /// Returns a local DateTime based on provided unix timestamp
        /// </summary>
        /// <param name="timestamp">Unix/posix timestamp</param>
        /// <returns>Local datetime</returns>
        public static DateTime ParseUnixTimestamp(long timestamp)
        {
            return (new DateTime(1970, 1, 1)).AddSeconds(timestamp).ToLocalTime();
        }
    }
}
