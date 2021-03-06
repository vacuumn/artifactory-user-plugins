/*
 * Copyright (C) 2014 JFrog Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.artifactory.api.repo.exception.ItemNotFoundRuntimeException

// curl command example for running this plugin.
// curl -i -uadmin:password -X POST "http://localhost:8081/artifactory/api/plugins/execute/cleanup?params=months=1|repos=libs-release-local|dryRun=true"
executions {
    cleanup() { params ->
        def months = params['months'] ? params['months'][0] as int : 6
        def repos = params['repos'] as String[]
        def dryRun = params['dryRun'] ? params['dryRun'][0] as boolean : false
        artifactCleanup(months, repos, log, dryRun)
    }
}

jobs {
    scheduledCleanup(cron: "0 0 5 ? * 1") {
        def config = new ConfigSlurper().parse(new File(ctx.artifactoryHome.haAwareEtcDir, "plugins/artifactCleanup.properties").toURL())
        artifactCleanup(config.monthsUntil, config.repos as String[], log)
    }
}

private def artifactCleanup(int months, String[] repos, log, dryRun = false) {
    log.info "Starting artifact cleanup for repositories $repos, until $months months ago"

    def monthsUntil = Calendar.getInstance()
    monthsUntil.add(Calendar.MONTH, -months)

    long bytesFound = 0
    def artifactsCleanedUp =
        searches.artifactsNotDownloadedSince(monthsUntil, Calendar.getInstance(), repos). each {
            def created = Calendar.getInstance()
            created.setTimeInMillis(repositories.getItemInfo(it)?.created)
            if (monthsUntil.after(created)) {
                try {
                    bytesFound += repositories.getItemInfo(it)?.getSize()
                    if (dryRun) {
                        log.info "Found $it"
                    } else {
                        log.info "Deleting $it"
                        repositories.delete it
                    }
                } catch (ItemNotFoundRuntimeException ex) {
                    log.info "Item $it not found, may have already been deleted"
                }
            }
        }

    if (dryRun) {
        log.info "Dry run - nothing deleted. found $artifactsCleanedUp.size artifacts consuming $bytesFound bytes"
    } else {
        log.info "Finished cleanup, deleted $artifactsCleanedUp.size artifacts that took up $bytesFound bytes"
    }
}
