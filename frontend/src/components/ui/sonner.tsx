"use client"

import { Toaster as Sonner, type ToasterProps } from "sonner"
import { CircleCheckIcon, InfoIcon, TriangleAlertIcon, OctagonXIcon, Loader2Icon } from "lucide-react"

const Toaster = ({ ...props }: ToasterProps) => {

  return (
    <Sonner
      theme="light"
      richColors
      closeButton
      className="toaster group"
      icons={{
        success: (
          <CircleCheckIcon className="size-4" />
        ),
        info: (
          <InfoIcon className="size-4" />
        ),
        warning: (
          <TriangleAlertIcon className="size-4" />
        ),
        error: (
          <OctagonXIcon className="size-4" />
        ),
        loading: (
          <Loader2Icon className="size-4 animate-spin" />
        ),
      }}
      style={
        {
          "--normal-bg": "var(--popover)",
          "--normal-text": "var(--popover-foreground)",
          "--normal-border": "var(--border)",
          "--border-radius": "var(--radius)",
          // richColors usa estas variables por tono; se apuntan a la paleta de mensajes de index.css.
          "--success-bg": "var(--success-soft)",
          "--success-border": "var(--success-line)",
          "--success-text": "var(--success-ink)",
          "--info-bg": "var(--info-soft)",
          "--info-border": "var(--info-line)",
          "--info-text": "var(--info-ink)",
          "--warning-bg": "var(--warning-soft)",
          "--warning-border": "var(--warning-line)",
          "--warning-text": "var(--warning-ink)",
          "--error-bg": "var(--destructive-soft)",
          "--error-border": "var(--destructive-line)",
          "--error-text": "var(--destructive-ink)",
        } as React.CSSProperties
      }
      toastOptions={{
        closeButtonAriaLabel: "Cerrar notificación",
        classNames: {
          toast: "cn-toast",
        },
      }}
      {...props}
    />
  )
}

export { Toaster }
