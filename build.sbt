name          := "asdfjkl"
organization  := "era7bio"
description   := "asdfjkl project"

scalaVersion  := "2.11.8"

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "era7bio"       %% "repseqmiodx" % "0.0.0-484-ge5fc0c1",
  "ohnosequences" %% "loquat"      % "2.0.0-RC4",
  "org.scalatest" %% "scalatest"   % "3.0.4" % Test
)

dependencyOverrides ++= Seq(
  "ohnosequences" %% "aws-scala-tools" % "0.19.0",
  "ohnosequences" %% "cosas" % "0.10.0"
)

// Uncomment if you need to deploy this project as a Statika bundle:
generateStatikaMetadataIn(Compile)

// TODO: this should be reviewed
wartremoverErrors in (Test, compile) := Seq()
wartremoverErrors in (Compile, compile) := Seq()


assemblyMergeStrategy in assembly := {
  case x if Assembly.isConfigFile(x) =>
      MergeStrategy.concat
    case PathList(ps @ _*) if Assembly.isReadme(ps.last) || Assembly.isLicenseFile(ps.last) =>
      MergeStrategy.rename
    case PathList("META-INF", xs @ _*) =>
      (xs map {_.toLowerCase}) match {
        case ("manifest.mf" :: Nil) | ("index.list" :: Nil) | ("dependencies" :: Nil) =>
          MergeStrategy.discard
        case ps @ (x :: xs) if ps.last.endsWith(".sf") || ps.last.endsWith(".dsa") =>
          MergeStrategy.discard
        case "plexus" :: xs =>
          MergeStrategy.discard
        case "services" :: xs =>
          MergeStrategy.filterDistinctLines
        case ("spring.schemas" :: Nil) | ("spring.handlers" :: Nil) =>
          MergeStrategy.filterDistinctLines
        case _ => MergeStrategy.first
      }
    case _ => MergeStrategy.first
}
