interface Props {
  name: string
  welcomeMessage: string
  primaryColor: string
}

export function WidgetPreview({ name, welcomeMessage, primaryColor }: Props) {
  return (
    <div className="flex flex-col items-center justify-center py-8">
      <p className="text-xs text-stone-400 mb-4 uppercase tracking-wide">Live Preview</p>
      <div className="w-72 rounded-2xl shadow-xl overflow-hidden border border-stone-200">
        {/* Header */}
        <div className="px-4 py-3 flex items-center gap-3" style={{ backgroundColor: primaryColor }}>
          <div className="h-8 w-8 rounded-full bg-white/20 flex items-center justify-center">
            <span className="text-white text-sm font-bold">{name.charAt(0).toUpperCase()}</span>
          </div>
          <span className="text-white font-semibold text-sm">{name}</span>
        </div>
        {/* Chat area */}
        <div className="bg-white px-4 py-4 min-h-32">
          <div className="flex items-start gap-2">
            <div className="h-6 w-6 rounded-full shrink-0 flex items-center justify-center" style={{ backgroundColor: primaryColor }}>
              <span className="text-white text-xs font-bold">{name.charAt(0).toUpperCase()}</span>
            </div>
            <div className="rounded-lg rounded-tl-none bg-stone-100 px-3 py-2 text-sm text-stone-800 max-w-52">
              {welcomeMessage}
            </div>
          </div>
        </div>
        {/* Input */}
        <div className="bg-white border-t border-stone-100 px-3 py-2 flex items-center gap-2">
          <div className="flex-1 rounded-full bg-stone-100 px-3 py-1.5 text-xs text-stone-400">
            Type a message…
          </div>
          <div className="h-7 w-7 rounded-full flex items-center justify-center shrink-0" style={{ backgroundColor: primaryColor }}>
            <svg className="h-3.5 w-3.5 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M12 19l9 2-9-18-9 18 9-2zm0 0v-8" />
            </svg>
          </div>
        </div>
      </div>
    </div>
  )
}
