public class PhpTypeTest extends <error descr="Cannot resolve symbol 'String1'">String1</error> {
  def foo() {
    print abc.class
  }
}
