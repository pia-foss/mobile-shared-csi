Pod::Spec.new do |spec|
    spec.name                     = 'PIACSIModule'
    spec.version                  = '1.0.0'
    spec.homepage                 = 'https://www.privateinternetaccess.com'
    spec.source            		  = { :git => "https://github.com/pia-foss/mobile-common-csi.git", :tag => "v#{spec.version}" }
    spec.authors                  = { "Jose Blaya" => "jose@privateinternetaccess.com", "Juan Docal" => "juan@privateinternetaccess.com" }
    spec.license                  = { :type => "MIT", :file => "LICENSE" }
    spec.summary                  = 'PIACSI Module'
    spec.ios.deployment_target    = "11.0"

    spec.vendored_frameworks      = "build/cocoapods/framework/PIACSI.framework"

    spec.source_files = "gradlew", "settings.gradle", "gradle.properties", "build.gradle"

    spec.pod_target_xcconfig = {
        'KOTLIN_TARGET[sdk=iphonesimulator*]' => 'ios_x64',
        'KOTLIN_TARGET[sdk=iphoneos*]' => 'ios_arm',
        'KOTLIN_TARGET[sdk=watchsimulator*]' => 'watchos_x86',
        'KOTLIN_TARGET[sdk=watchos*]' => 'watchos_arm',
        'KOTLIN_TARGET[sdk=appletvsimulator*]' => 'tvos_x64',
        'KOTLIN_TARGET[sdk=appletvos*]' => 'tvos_arm64',
        'KOTLIN_TARGET[sdk=macosx*]' => 'macos_x64'
    }

    spec.subspec "gradle" do |p|

        p.source_files = "gradle/**/*"

    end

    spec.subspec "csi" do |p|

        p.source_files = "src/**/*"

    end

    spec.script_phases = [
        {
            :name => 'Build csi',
            :execution_position => :before_compile,
            :shell_path => '/bin/sh',
            :script => <<-SCRIPT
                set -ev
                REPO_ROOT="$PODS_TARGET_SRCROOT"
                "$REPO_ROOT/gradlew" -p "$REPO_ROOT" :syncFramework \
                    -Pkotlin.native.cocoapods.target=$KOTLIN_TARGET \
                    -Pkotlin.native.cocoapods.configuration=$CONFIGURATION \
                    -Pkotlin.native.cocoapods.cflags="$OTHER_CFLAGS" \
                    -Pkotlin.native.cocoapods.paths.headers="$HEADER_SEARCH_PATHS" \
                    -Pkotlin.native.cocoapods.paths.frameworks="$FRAMEWORK_SEARCH_PATHS"
            SCRIPT
        }
    ]
end