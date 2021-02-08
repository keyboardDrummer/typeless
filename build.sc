import mill._, scalalib._
import coursier.maven.MavenRepository


object server extends ScalaModule {

  override def repositories = super.repositories ++ Seq(
    // Not sure if this is used
    MavenRepository("https://oss.sonatype.org/content/repositories/releases"),
    // Not sure if this is used
    MavenRepository("https://dl.bintray.com/dhpcs/maven")
  )

  def scalaVersion = "2.13.3"
  override def ivyDeps = Agg(ivy"com.github.keyboardDrummer::languageserver::0.1.8")

  object test extends Tests {
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest::3.1.1"
    )
    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }
}