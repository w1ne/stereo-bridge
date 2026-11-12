import unittest
from pathlib import Path
import tempfile
from transform import clone_manifest

class CloneManifestTest(unittest.TestCase):
    def test_separates_identity_without_changing_component_classes(self):
        source='''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.google.android.GoogleCamera"><application android:name="com.google.android.apps.camera.CameraApp" android:label="@string/app_name"><provider android:name="some.Provider" android:authorities="com.google.android.GoogleCamera.fileprovider"/><provider android:name="another.Provider" android:authorities="com.google.android.apps.camera.specialtypes.SpecialTypesProvider"/></application></manifest>'''
        result=clone_manifest(source,'app.stereobridge.camera.test','Stereo Camera · Test')
        import xml.etree.ElementTree as E
        root=E.fromstring(result);a='{http://schemas.android.com/apk/res/android}'
        self.assertEqual(root.get('package'),'app.stereobridge.camera.test')
        app=root.find('application')
        self.assertEqual(app.get(a+'name'),'com.google.android.apps.camera.CameraApp')
        self.assertEqual(app.get(a+'label'),'Stereo Camera · Test')
        for p in app.findall('provider'):
            self.assertTrue(p.get(a+'authorities').startswith('app.stereobridge.camera.test.'))
        self.assertEqual(len({p.get(a+'authorities') for p in app.findall('provider')}),2)

if __name__=='__main__': unittest.main()
