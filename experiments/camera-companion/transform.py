"""Narrow local-only transformations; never replace an installed package."""
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
ET.register_namespace('android', ANDROID[1:-1])

def authority_map(source, package):
    root = ET.fromstring(source)
    return {authority: package + '.provider.' + authority
            for provider in root.findall('application/provider')
            for authority in provider.get(ANDROID + 'authorities', '').split(';')
            if authority}

def clone_manifest(source, package, label):
    root = ET.fromstring(source)
    original = root.get('package')
    if not original or package == original:
        raise ValueError('A separate package identity is required')
    root.set('package', package)
    mapping = authority_map(source, package)
    permission_mapping = {}
    for node in root.findall('permission'):
        old = node.get(ANDROID + 'name')
        if old:
            permission_mapping[old] = package + '.permission.' + old
    for node in root.iter():
        for key in ('name', 'permission', 'readPermission', 'writePermission'):
            value = node.get(ANDROID + key)
            if value in permission_mapping:
                node.set(ANDROID + key, permission_mapping[value])
    app = root.find('application')
    if app is None:
        raise ValueError('Missing application')
    app.set(ANDROID + 'label', label)
    app.set(ANDROID + 'allowBackup', 'false')
    for node in app.iter():
        if node.tag in ('application', 'activity', 'activity-alias', 'service', 'receiver', 'provider'):
            for attr in ('name', 'targetActivity', 'backupAgent', 'appComponentFactory'):
                name = node.get(ANDROID + attr)
                if name and name.startswith('.'):
                    node.set(ANDROID + attr, original + name)
        authorities = node.get(ANDROID + 'authorities')
        if authorities:
            node.set(ANDROID + 'authorities', ';'.join(mapping[a] for a in authorities.split(';')))
    return ET.tostring(root, encoding='unicode')
