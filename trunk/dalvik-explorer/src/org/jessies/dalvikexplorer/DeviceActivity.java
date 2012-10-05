package org.jessies.dalvikexplorer;

import android.app.*;
import android.content.pm.*;
import android.os.*;
import android.util.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.regex.*;

public class DeviceActivity extends TextViewActivity {
  protected CharSequence title(String unused) {
    return "Device Details";
  }
  
  protected CharSequence content(String unused) {
    return getDeviceDetailsAsString(this, getWindowManager());
  }
  
  // sysconf _SC_NPROCESSORS_CONF and _SC_NPROCESSORS_ONLN have been broken
  // in bionic for various different reasons. /proc parsing was broken until
  // Gingerbread, and even then _SC_NPROCESSORS_CONF was broken because ARM
  // kernels remove offline processors from both /proc/stat and /proc/cpuinfo
  // unlike x86 ones; you need to look in /sys/devices/system/cpu to see all
  // the processors. This should be fixed some time post-JB.
  private static int countHardwareCores() {
    int result = 0;
    for (String file : new File("/sys/devices/system/cpu").list()) {
      if (file.matches("cpu[0-9]+")) {
        ++result;
      }
    }
    return result;
  }
  
  private static int countEnabledCores() {
    int count = 0;
    BufferedReader in = null;
    try {
      in = new BufferedReader(new FileReader("/proc/stat"));
      String line;
      while ((line = in.readLine()) != null) {
        if (line.startsWith("cpu") && !line.startsWith("cpu ")) {
          ++count;
        }
      }
      return count;
    } catch (IOException ex) {
      return -1;
    } finally {
      if (in != null) {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
  
  private static String valueForKey(String[] lines, String key) {
    // If you touch this, test on ARM, MIPS, and x86.
    Pattern p = Pattern.compile("(?i)" + key + "\t*: (.*)");
    for (String line : lines) {
      Matcher m = p.matcher(line);
      if (m.matches()) {
        return m.group(1);
      }
    }
    return null;
  }
  
  private static int numericValueForKey(String[] lines, String key) {
    String value = valueForKey(lines, key);
    if (value == null) {
      return -1;
    }
    int base = 10;
    if (value.startsWith("0x")) {
      base = 16;
      value = value.substring(2);
    }
    try {
      return Integer.valueOf(value, base);
    } catch (NumberFormatException ex) {
      return -1;
    }
  }
  
  private static String decodeImplementer(int implementer) {
    // From http://infocenter.arm.com/help/index.jsp?topic=/com.arm.doc.ihi0014q/Bcfihfdj.html
    if (implementer == 0x41) {
      return "ARM";
    } else if (implementer == 0x44) {
      return "Digital Equipment Corporation";
    } else if (implementer == 0x4d) {
      return "Motorola";
    } else if (implementer == 0x51) {
      return "Qualcomm";
    } else if (implementer == 0x56) {
      return "Marvell";
    } else if (implementer == 0x69) {
      return "Intel";
    } else {
      return "unknown (0x" + Integer.toHexString(implementer) + ")";
    }
  }
  
  private static String decodePartNumber(int part) {
    if (part == 0x920) {
      return "ARM920";
    } else if (part == 0x926) {
      return "ARM926";
    } else if (part == 0xa26) {
      return "ARM1026";
    } else if (part == 0xb02) {
      return "ARM11mpcore";
    } else if (part == 0xb36) {
      return "ARM1136";
    } else if (part == 0xb56) {
      return "ARM1156";
    } else if (part == 0xb76) {
      return "ARM1176";
    } else if (part == 0xc05) {
      return "Cortex-A5";
    } else if (part == 0xc07) {
      return "Cortex-A7";
    } else if (part == 0xc08) {
      return "Cortex-A8";
    } else if (part == 0xc09) {
      return "Cortex-A9";
    } else if (part == 0xc0f) {
      return "Cortex-A15";
    } else if (part == 0xc14) {
      return "Cortex-R4";
    } else if (part == 0xc15) {
      return "Cortex-R5";
    } else if (part == 0xc20) {
      return "Cortex-M0";
    } else if (part == 0xc21) {
      return "Cortex-M1";
    } else if (part == 0xc23) {
      return "Cortex-M3";
    } else if (part == 0xc24) {
      return "Cortex-M4";
    } else {
      return "unknown (0x" + Integer.toHexString(part) + ")";
    }
  }
  
  static String getDeviceDetailsAsString(Activity context, WindowManager wm) {
    final StringBuilder result = new StringBuilder();
    
    String[] procCpuLines = Utils.readFile("/proc/cpuinfo").split("\n");
    // x86 kernels use "processor" as an integer to number sockets.
    // They use "model name" to describe the processor.
    // ARM kernels use "Processor" to describe the processor.
    String processor = valueForKey(procCpuLines, "model name");
    if (processor == null) {
      processor = valueForKey(procCpuLines, "Processor");
    }
    result.append("Processor: " + processor + "\n");
    
    int hardwareCoreCount = countHardwareCores();
    int enabledCoreCount = countEnabledCores();
    String cores = Integer.toString(hardwareCoreCount);
    if (enabledCoreCount != hardwareCoreCount) {
      cores += " (enabled: " + enabledCoreCount + ")";
    }
    result.append("Cores: " +  cores + "\n");
    result.append('\n');
    
    try {
      String minFrequency = Utils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_min_freq").trim();
      String maxFrequency = Utils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq").trim();
      int minFrequencyHz = Integer.parseInt(minFrequency) * 1000;
      int maxFrequencyHz = Integer.parseInt(maxFrequency) * 1000;
      result.append("CPU Speed: " + Utils.prettyHz(maxFrequencyHz) + " (idles at " + Utils.prettyHz(minFrequencyHz) + ")\n");
    } catch (Exception unexpected) {
      result.append("(Unable to determine CPU frequencies.)\n");
    }
    result.append('\n');
    
    // ARM-specific.
    int implementer = numericValueForKey(procCpuLines, "CPU implementer");
    if (implementer != -1) {
      result.append("CPU Implementer: " + decodeImplementer(implementer) + "\n");
      result.append("CPU Part: " + decodePartNumber(numericValueForKey(procCpuLines, "CPU part")) + "\n");
      // These two are included in the kernel's formatting of "Processor".
      //result.append("CPU Architecture: " + numericValueForKey(procCpuLines, "CPU architecture") + "\n");
      //result.append("CPU Revision: " + numericValueForKey(procCpuLines, "CPU revision") + "\n");
      result.append("CPU Variant: " + numericValueForKey(procCpuLines, "CPU variant") + "\n");
      result.append('\n');
      result.append("Hardware: " + valueForKey(procCpuLines, "Hardware") + "\n");
      result.append("Revision: " + valueForKey(procCpuLines, "Revision") + "\n");
      result.append("Serial: " + valueForKey(procCpuLines, "Serial") + "\n");
      result.append('\n');
    }
    
    // MIPS-specific.
    // TODO: is "CPU architecture" ever more specific than "MIPS"?
    if ("MIPS".equals(valueForKey(procCpuLines, "CPU architecture"))) {
      result.append("CPU Implementer: " + valueForKey(procCpuLines, "CPU implementer") + "\n");
      result.append("CPU Model: " + valueForKey(procCpuLines, "cpu model") + "\n");
      result.append('\n');
      result.append("Hardware: " + valueForKey(procCpuLines, "Hardware") + "\n");
      result.append("Revision: " + valueForKey(procCpuLines, "Revision") + "\n");
      result.append("Serial: " + valueForKey(procCpuLines, "Serial") + "\n");
      result.append('\n');
    }
    
    // Intel-specific.
    String cacheSize = valueForKey(procCpuLines, "cache size");
    String addressSizes = valueForKey(procCpuLines, "address sizes");
    if (cacheSize != null) {
      result.append("Cache: " + cacheSize + "\n");
      result.append("Address Sizes: " + addressSizes + "\n");
      result.append('\n');
    }
    
    String features = valueForKey(procCpuLines, "Features");
    if (features == null) {
      features = valueForKey(procCpuLines, "flags");
    }
    result.append("CPU Features:\n");
    result.append(Utils.sortedStringOfStrings(features.split(" ")));
    result.append('\n');
    
    MemInfo memInfo = readMemInfo();
    result.append("Memory:\n");
    result.append("  Total: " + Utils.prettySize(memInfo.total) + "\n");
    result.append("  Used: " + Utils.prettySize(memInfo.used) + "\n");
    result.append("  Free: " + Utils.prettySize(memInfo.free) + "\n");
    result.append("  Buffers: " + Utils.prettySize(memInfo.buffers) + "\n");
    result.append('\n');
    
    DisplayMetrics metrics = new DisplayMetrics();
    wm.getDefaultDisplay().getMetrics(metrics);
    result.append("Screen Density: " + metrics.densityDpi + "dpi (" + metrics.density + "x DIP)\n");
    result.append("Screen Size: " + metrics.widthPixels + " x " + metrics.heightPixels + " pixels\n");
    result.append("Exact DPI: " + metrics.xdpi + " x " + metrics.ydpi + "\n");
    double widthInches = metrics.widthPixels/metrics.xdpi;
    double heightInches = metrics.heightPixels/metrics.ydpi;
    double diagonalInches = Math.sqrt(widthInches*widthInches + heightInches*heightInches);
    result.append(String.format("Approximate Dimensions: %.1f\" x %.1f\" (%.1f\" diagonal)\n", widthInches, heightInches, diagonalInches));
    result.append('\n');
    
    return result.toString();
  }
  
  private static String getFieldReflectively(Build build, String fieldName) {
    try {
      final Field field = Build.class.getField(fieldName);
      return field.get(build).toString();
    } catch (Exception ex) {
      return "unknown";
    }
  }
  
  static class MemInfo {
    long total;
    long used;
    long free;
    long buffers;
  }
  
  private static MemInfo readMemInfo() {
    MemInfo result = new MemInfo();
    String[] lines = Utils.readLines("/proc/meminfo");
    for (String line : lines) {
      String[] fields = line.split(" +");
      if (fields[0].equals("MemTotal:")) {
        result.total = Long.parseLong(fields[1]) * 1024;
      } else if (fields[0].equals("MemFree:")) {
        result.free = Long.parseLong(fields[1]) * 1024;
      } else if (fields[0].equals("Buffers:")) {
        result.buffers = Long.parseLong(fields[1]) * 1024;
      } else {
        break;
      }
    }
    result.used = result.total - result.free;
    return result;
  }
}
