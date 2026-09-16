import {readonly, ref} from 'vue'
import {demoAccounts, type DemoAccount} from '~/api/demo'

const accounts = ref<DemoAccount[] | null>(null)
const checked = ref(false)
let asking: Promise<void> | null = null

/**
 * Whether this instance is a demo, and who it offers to sign in as.
 *
 * <p>Asked once and remembered: every page that wants to know asks the same question, and a real
 * instance answering 404 is a stable answer rather than something to retry.
 */
async function load(): Promise<void> {
    if (asking) return asking
    asking = (async () => {
        accounts.value = await demoAccounts()
        checked.value = true
    })()
    return asking
}

export function useDemo() {
    return {
        accounts: readonly(accounts),
        checked: readonly(checked),
        isDemo: () => accounts.value !== null,
        load,
    }
}
