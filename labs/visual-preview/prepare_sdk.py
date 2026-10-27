"""Fetch a local API 35 compile SDK from Google's repository, verifying published SHA-1.

Does not alter local.properties, user configuration, or accept licenses on the user's behalf.
Archives retain their bundled notices. SDK is under ignored build/; pass ANDROID_HOME explicitly.
"""
from pathlib import Path, PurePosixPath
import hashlib
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1] / 'app-desktop' / 'build' / 'android-sdk'
BASE = 'https://dl.google.com/android/repository/'


def main():
    repository = ET.fromstring(urllib.request.urlopen(BASE + 'repository2-1.xml').read())
    packages = {'platforms;android-35': 'platform-35_r02.zip',
                'build-tools;35.0.0': 'build-tools_r35_windows.zip',
                'platform-tools': None}
    for package, desired in packages.items():
        destination = ROOT.joinpath(*package.split(';'))
        if (destination / 'source.properties').exists():
            print('Already installed:', package, flush=True)
            continue
        candidates = [archive for node in repository if node.attrib.get('path') == package
                      for archive in node.findall('archives/archive')
                      if archive.findtext('host-os') in (None, 'windows')
                      and (desired is None or archive.findtext('complete/url') == desired)]
        archive = candidates[0]
        url = archive.findtext('complete/url')
        print('Downloading:', url, flush=True)
        data = urllib.request.urlopen(BASE + url).read()
        assert hashlib.sha1(data).hexdigest() == archive.findtext('complete/checksum'), url
        ROOT.mkdir(parents=True, exist_ok=True)
        download = ROOT / url
        download.write_bytes(data)
        with zipfile.ZipFile(download) as bundle:
            for item in bundle.infolist():
                parts = PurePosixPath(item.filename).parts[1:]
                if not parts:
                    continue
                target = destination.joinpath(*parts).resolve()
                assert target.is_relative_to(destination.resolve()), item.filename
                if item.is_dir():
                    target.mkdir(parents=True, exist_ok=True)
                else:
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(bundle.read(item))
        print('Installed:', destination, flush=True)
    print('ANDROID_HOME=' + str(ROOT), flush=True)


if __name__ == '__main__':
    main()
