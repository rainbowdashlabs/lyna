/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref, watch} from 'vue'
import {listGuildProducts, type ProductSummary, updateGuildProduct} from '~/api/admin'

const {t} = useI18n()

const props = defineProps<{ guildId: string, product: ProductSummary }>()
const emit = defineEmits<{ saved: [] }>()

const name = ref(props.product.name)
const url = ref(props.product.url ?? '')
const roleId = ref(props.product.roleId)
const free = ref(props.product.free)
const trial = ref(props.product.trial)
const butlerId = ref(props.product.butlerId?.toString() ?? '')
const wikiUrl = ref(props.product.wikiUrl ?? '')
const reviewUrl = ref(props.product.reviewUrl ?? '')
const parentId = ref<number | null>(props.product.parentId ?? null)
const guildProducts = ref<ProductSummary[]>([])
const iconUrl = ref(props.product.iconAddress ?? '')
watch(() => props.product.iconAddress, next => {
  iconUrl.value = next ?? ''
})

/** A family is one level deep: an add-on has no add-ons, and a product that has some is no add-on. */
const parentCandidates = computed(() => guildProducts.value.filter(candidate =>
    candidate.id !== props.product.id && candidate.parentId == null))
const hasAddons = computed(() => guildProducts.value.some(candidate => candidate.parentId === props.product.id))

onMounted(async () => {
  try {
    guildProducts.value = await listGuildProducts(props.guildId)
  } catch {
    guildProducts.value = []
  }
})

const busy = ref(false)
const errorMessage = ref<string | null>(null)
const saved = ref(false)

/**
 * Saves the whole product at once.
 *
 * <p>One request rather than one per field: a form that saves the name and then fails on the role has
 * already changed half of what somebody asked for.
 */
async function save() {
  busy.value = true
  errorMessage.value = null
  saved.value = false
  try {
    await updateGuildProduct(props.guildId, props.product.id, {
      name: name.value,
      url: url.value.trim() ? url.value.trim() : null,
      roleId: roleId.value,
      free: free.value,
      trial: trial.value,
      iconUrl: iconUrl.value.trim() ? iconUrl.value.trim() : null,
      butlerId: butlerId.value.trim() ? Number(butlerId.value.trim()) : null,
      wikiUrl: wikiUrl.value.trim() || null,
      reviewUrl: reviewUrl.value.trim() || null,
      parentId: parentId.value,
    })
    saved.value = true
    emit('saved')
  } catch (e) {
    const error = e as { response?: { data?: string } }
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : t('ui.productEditForm.couldNotSave')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <form class="space-y-3" @submit.prevent="save">
    <LabelledField :label="t('ui.productEditForm.name')">
      <TextInput v-model="name" required/>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.roleId')">
      <RolePicker v-model="roleId" :guild-id="guildId"/>
    </LabelledField>
    <LabelledField :label="t('common.addon.belongsTo')">
      <SelectInput v-model="parentId" :disabled="hasAddons" data-testid="product-parent">
        <option :value="null">{{ t('ui.productEditForm.standsOnItsOwn') }}</option>
        <option v-for="candidate in parentCandidates" :key="candidate.id" :value="candidate.id">{{ candidate.name }}</option>
      </SelectInput>
      <MutedText size="xs" tag="p">
        {{ hasAddons ? t('ui.productEditForm.hasAddonsHint') : t('ui.productEditForm.belongsToHint') }}
      </MutedText>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.projectUrl')">
      <TextInput v-model="url" type="url"/>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.wikiUrl')">
      <TextInput v-model="wikiUrl" type="url"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.wikiHint') }}</MutedText>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.reviewUrl')">
      <TextInput v-model="reviewUrl" type="url"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.reviewHint') }}</MutedText>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.iconAddress')">
      <TextInput v-model="iconUrl" type="url"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.usedWhenNothingIsUploaded') }}</MutedText>
    </LabelledField>
    <LabelledField :label="t('ui.productEditForm.butlerId')">
      <TextInput v-model="butlerId" inputmode="numeric" pattern="[0-9]*"/>
      <MutedText size="xs" tag="p">{{ t('ui.productEditForm.butlerIdHint') }}</MutedText>
    </LabelledField>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="trial"/> {{ t('ui.productEditForm.trialAvailable') }}
    </label>
    <label class="flex items-center gap-2 text-sm">
      <CheckboxInput v-model="free"/> {{ t('ui.productEditForm.freeToEveryone') }}
    </label>
    <MutedText v-if="free !== product.free" class="block text-error" size="xs" tag="p">
      {{ t('ui.productEditForm.thisChangesWhoMayDownloadIt') }}
    </MutedText>
    <div class="flex items-center gap-3">
      <PrimaryButton :disabled="busy" type="submit">{{ t('ui.productEditForm.save') }}</PrimaryButton>
      <MutedText v-if="saved" size="sm">{{ t('ui.productEditForm.saved') }}</MutedText>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" size="sm">{{ errorMessage }}</MutedText>
  </form>
</template>
