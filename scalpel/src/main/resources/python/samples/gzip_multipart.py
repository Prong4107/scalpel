from pyscalpel.http import Request, Response
from pyscalpel.burp_utils import logger
import gzip


def unzip_bytes(data):
    try:
        # Create a GzipFile object with the input data
        with gzip.GzipFile(fileobj=data) as gz_file:
            # Read the uncompressed data
            uncompressed_data = gz_file.read()
        return uncompressed_data
    except OSError as e:
        logger.logToError(f"Error: Failed to unzip the data - {e}")


def req_edit_in_fs(req: Request) -> bytes | None:
    gz = req.multipart_form["fs"].content
    content = gzip.decompress(gz).decode("utf-16le").encode("latin-1")
    return content


def req_edit_out_fs(req: Request, text: bytes) -> Request | None:
    data = text.decode("latin-1").encode("utf-16le")
    content = gzip.compress(data, mtime=0)
    req.multipart_form["fs"].content = content
    return req


def req_edit_in_filetosend(req: Request) -> bytes | None:
    gz = req.multipart_form["filetosend"].content
    content = gzip.decompress(gz)
    return content


def req_edit_out_filetosend(req: Request, text: bytes) -> Request | None:
    data = text
    content = gzip.compress(data, mtime=0)
    req.multipart_form["filetosend"].content = content
    return req


def res_edit_in(res: Response) -> bytes | None:
    gz = res.content
    if not gz:
        return

    content = gzip.decompress(gz)
    content.decode("utf-16le").encode("utf-8")
    return content


def res_edit_out(res: Response, text: bytes) -> Response | None:
    res.content = text
    return res
