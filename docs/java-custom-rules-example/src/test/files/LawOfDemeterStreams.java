
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class StreamClass {
  public List<Integer> foo5() {
    List<Integer> integerList = List.of(1, 2, 3, 4, 5, 6);
    List<Integer> filteredList = integerList
      .stream()
      .map(i -> i + 1)
      .filter(i -> i > 10)
      .distinct()
      .collect(Collectors.toList());

      return filteredList;
  }
}

