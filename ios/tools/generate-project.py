"""Generate a dependency-free, deterministic Xcode app project from checked-in files."""
from pathlib import Path
import hashlib

ROOT = Path(__file__).resolve().parents[1]
project = ROOT / 'Miniscore.xcodeproj'
project.mkdir(exist_ok=True)

def identifier(value):
    return hashlib.sha256(value.encode()).hexdigest()[:24].upper()

objects = []
def obj(key, value):
    ident = identifier(key)
    objects.append(f'{ident} = {{ {value} }};')
    return ident

sources = sorted((ROOT / 'Miniscore').glob('*.swift')) + sorted((ROOT / 'Sources/MiniscoreCore').glob('*.swift'))
resources = [ROOT / 'Miniscore/Assets.xcassets', ROOT / 'Miniscore/PrivacyInfo.xcprivacy', ROOT / 'Miniscore/SampleGames.json']
source_refs, resource_refs, source_builds, resource_builds = [], [], [], []
for path in sources + resources:
    relative = path.relative_to(ROOT).as_posix()
    file_type = 'sourcecode.swift' if path in sources else 'folder.assetcatalog' if path.suffix == '.xcassets' else 'text.json' if path.suffix == '.json' else 'text.xml'
    ref = obj('file:' + relative, f'isa = PBXFileReference; lastKnownFileType = {file_type}; path = "{relative}"; sourceTree = SOURCE_ROOT;')
    build = obj('build:' + relative, f'isa = PBXBuildFile; fileRef = {ref};')
    (source_refs if path in sources else resource_refs).append(ref)
    (source_builds if path in sources else resource_builds).append(build)

def listing(values):
    return '(' + ', '.join(values) + ',)'

product = obj('product', 'isa = PBXFileReference; explicitFileType = wrapper.application; path = Miniscore.app; sourceTree = BUILT_PRODUCTS_DIR;')
products = obj('products', f'isa = PBXGroup; children = ({product},); name = Products; sourceTree = "<group>";')
group = obj('rootgroup', f'isa = PBXGroup; children = {listing(source_refs + resource_refs + [products])}; sourceTree = "<group>";')
source_phase = obj('sources', f'isa = PBXSourcesBuildPhase; buildActionMask = 2147483647; files = {listing(source_builds)}; runOnlyForDeploymentPostprocessing = 0;')
resource_phase = obj('resources', f'isa = PBXResourcesBuildPhase; buildActionMask = 2147483647; files = {listing(resource_builds)}; runOnlyForDeploymentPostprocessing = 0;')
framework_phase = obj('frameworks', 'isa = PBXFrameworksBuildPhase; buildActionMask = 2147483647; files = (); runOnlyForDeploymentPostprocessing = 0;')
project_configs, target_configs = [], []
for name in ['Debug', 'Release']:
    project_configs.append(obj('project:' + name, f'isa = XCBuildConfiguration; name = {name}; buildSettings = {{ CLANG_ENABLE_MODULES = YES; SDKROOT = iphoneos; IPHONEOS_DEPLOYMENT_TARGET = 17.0; }};'))
    optimization = 'SWIFT_OPTIMIZATION_LEVEL = "-Onone"; SWIFT_ACTIVE_COMPILATION_CONDITIONS = DEBUG;' if name == 'Debug' else 'SWIFT_OPTIMIZATION_LEVEL = "-O"; SWIFT_COMPILATION_MODE = wholemodule;'
    target_configs.append(obj('target:' + name, f'''isa = XCBuildConfiguration; name = {name}; buildSettings = {{
        ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon; CODE_SIGN_STYLE = Automatic;
        CURRENT_PROJECT_VERSION = 1; DEVELOPMENT_TEAM = 36HS92C4DU; ENABLE_PREVIEWS = YES;
        GENERATE_INFOPLIST_FILE = NO; INFOPLIST_FILE = Miniscore/Info.plist;
        LD_RUNPATH_SEARCH_PATHS = "$(inherited) @executable_path/Frameworks";
        MARKETING_VERSION = 1.0.0; PRODUCT_BUNDLE_IDENTIFIER = com.ivicastojanoski.miniscore;
        PRODUCT_NAME = "$(TARGET_NAME)"; SUPPORTED_PLATFORMS = "iphoneos iphonesimulator";
        SUPPORTS_MACCATALYST = NO; SWIFT_VERSION = 6.0; TARGETED_DEVICE_FAMILY = "1,2";
        {optimization}
    }};'''))
project_config_list = obj('projectconfigs', f'isa = XCConfigurationList; buildConfigurations = {listing(project_configs)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
target_config_list = obj('targetconfigs', f'isa = XCConfigurationList; buildConfigurations = {listing(target_configs)}; defaultConfigurationIsVisible = 0; defaultConfigurationName = Release;')
target = obj('target', f'''isa = PBXNativeTarget; buildConfigurationList = {target_config_list};
    buildPhases = ({source_phase}, {framework_phase}, {resource_phase},); buildRules = (); dependencies = ();
    name = Miniscore; productName = Miniscore; productReference = {product}; productType = "com.apple.product-type.application";''')
root = obj('project', f'''isa = PBXProject; attributes = {{ BuildIndependentTargetsInParallel = YES; LastUpgradeCheck = 2660;
    TargetAttributes = {{ {target} = {{ CreatedOnToolsVersion = 26.6; ProvisioningStyle = Automatic; }}; }}; }};
    buildConfigurationList = {project_config_list}; compatibilityVersion = "Xcode 14.0"; developmentRegion = en;
    hasScannedForEncodings = 0; knownRegions = (en, Base,); mainGroup = {group}; productRefGroup = {products};
    projectDirPath = ""; projectRoot = ""; targets = ({target},);''')
(project / 'project.pbxproj').write_text('// !$*UTF8*$!\n{ archiveVersion = 1; classes = {}; objectVersion = 56; objects = {\n' + '\n'.join(objects) + f'\n}}; rootObject = {root}; }}\n')
schemes = project / 'xcshareddata/xcschemes'
schemes.mkdir(parents=True, exist_ok=True)
(schemes / 'Miniscore.xcscheme').write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="2660" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries><BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="Miniscore.app" BlueprintName="Miniscore" ReferencedContainer="container:Miniscore.xcodeproj"/></BuildActionEntry></BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"/>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="Miniscore.app" BlueprintName="Miniscore" ReferencedContainer="container:Miniscore.xcodeproj"/></BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0"><BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="Miniscore.app" BlueprintName="Miniscore" ReferencedContainer="container:Miniscore.xcodeproj"/></BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>
''')
print(project)
