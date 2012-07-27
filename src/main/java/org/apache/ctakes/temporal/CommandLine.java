package org.apache.ctakes.temporal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandLine {

  public static class IntegerRanges {

    private List<Integer> items = new ArrayList<Integer>();

    public List<Integer> getList() {
      return this.items;
    }

    public IntegerRanges(String string) {
      for (String part : string.split("\\s*,\\s*")) {
        Matcher matcher = Pattern.compile("(\\d+)-(\\d+)").matcher(part);
        if (matcher.matches()) {
          int begin = Integer.parseInt(matcher.group(1));
          int end = Integer.parseInt(matcher.group(2));
          for (int i = begin; i <= end; ++i) {
            this.items.add(i);
          }
        } else {
          this.items.add(Integer.parseInt(part));
        }
      }
    }
  }
}
