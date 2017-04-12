package csw.services.config.server.repo

import java.nio.file.{Path, Paths}

import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Keep}
import csw.services.config.api.models.ConfigData
import csw.services.config.server.Settings

import scala.async.Async._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
  * The files are stored in the configured directory using a file name and directory structure
  * based on the SHA-1 hash of the file contents (This is the same way Git stores data).
  * The file checked in to the Svn repository is then named ''file''.`sha1` and contains only
  * the SHA-1 hash value.
  **/
class OversizeFileManager(settings: Settings, fileOps: FileOps) {

  def post(configData: ConfigData)(implicit mat: Materializer): Future[String] = async {
    val (tempFilePath, sha) = await(saveAndSha(configData))

    val outPath = makePath(settings.`oversize-files-dir`, sha)

    if (await(fileOps.exists(outPath))) {
      await(fileOps.delete(tempFilePath))
      sha
    }
    else {
      await(fileOps.createDirectories(outPath.getParent))
      await(fileOps.move(tempFilePath, outPath))
      if (await(validate(sha, outPath))) {
        sha
      }
      else {
        await(fileOps.delete(outPath))
        await(fileOps.delete(tempFilePath))
        throw new RuntimeException(s" Error in creating file for $sha")
      }
    }
  }

  def get(sha: String): Future[Option[ConfigData]] = async {
    val repoFilePath = makePath(settings.`oversize-files-dir`, sha)

    if (await(fileOps.exists(repoFilePath))) {
      Some(ConfigData.fromSource(FileIO.fromPath(repoFilePath)))
    } else {
      None
    }
  }

  // Returns the name of the file to use in the configured directory.
  // Like Git, distribute the files in directories based on the first 2 chars of the SHA-1 hash
  private def makePath(dir: String, file: String): Path = {
    val (subdir, name) = file.splitAt(2)
    Paths.get(dir, subdir, name)
  }

  /**
    * Verifies that the given file's content matches the SHA-1 id
    *
    * @param id   the SHA-1 of the file
    * @param path the file to check
    * @return true if the file is valid
    */
  def validate(id: String, path: Path)(implicit mat: Materializer): Future[Boolean] = async {
    id == await(Sha1.fromPath(path))
  }


  def saveAndSha(configData: ConfigData)(implicit mat: Materializer): Future[(Path, String)] = async {
    val path = await(fileOps.createTempFile("config-service-overize-", ".tmp"))
    val (resultF, shaF) = configData.source
      .alsoToMat(FileIO.toPath(path))(Keep.right)
      .toMat(Sha1.sink)(Keep.both)
      .run()
    await(resultF).status.get
    (path, await(shaF))
  }

}
