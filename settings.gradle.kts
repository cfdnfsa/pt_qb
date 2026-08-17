pluginManagement {
    repositories {
        // 腾讯云镜像（国内加速），官方源兜底
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/")
        // GeckoView 仅发布在 Mozilla 自家仓库（central 及其镜像都没有）
        maven("https://maven.mozilla.org/maven2/")
        google()
        mavenCentral()
    }
}
rootProject.name = "pt_qb"
include(":app")
