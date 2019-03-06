name          := "clonomapbridge"
organization  := "ohnosequences"
description   := "A bridge between MIODx webapp and MIODx analysis pipeline"

scalaVersion  := "2.11.12"

bucketSuffix  := "era7.com"

libraryDependencies ++= Seq(
  "ohnosequences" %% "repseqmiodx" % "0.1.0-4-g02c29c0",
  "org.ddahl"     %% "rscala"      % "2.2.2",
  "ohnosequences" %% "loquat"      % "2.0.0-RC4",
  "org.scalatest" %% "scalatest"   % "3.0.5" % Test
)

dependencyOverrides ++= Seq(
  "ohnosequences" %% "aws-scala-tools" % "0.19.0",
  "ohnosequences" %% "cosas" % "0.10.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
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
