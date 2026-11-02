import type { InjectionKey } from 'vue'
export type NumberMode = 'quantity' | 'price'
export interface NumberEntryRequest {
  label: string
  mode: NumberMode
  value: string
  commit: (value: string) => void
}
export const numberEntryKey: InjectionKey<(request: NumberEntryRequest) => void> = Symbol('catalog-number-entry')
