class Assert extends ObjectValue {
  members.put("strictEqual", AssertStrictEqual)
}

object StandardLibrary {
  def createState() = {
    val result = new Scope()
    result.declare("assert", new Assert())
    result
  }
}