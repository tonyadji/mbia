import { useEffect, useEffectEvent, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { IconButton } from '../components/IconButton';
import { PersonCard } from '../components/PersonCard';
import type { TreeNode } from '../persons/useFamilyTree';
import { ADD_SIZE, type PlacedAdd, type TreeLayout } from './treeLayout';

export const MIN_ZOOM = 0.5;
export const MAX_ZOOM = 1.5;
const ZOOM_STEP = 0.25;
const PADDING = 24;

const clampZoom = (value: number) => Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value));

const reducedMotion = () =>
  typeof window.matchMedia === 'function' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/**
 * The drawing of a tree layout (family-tree-ux.md §6.1): cards, lines and "Add…" affordances on a
 * canvas that pans when wider than the screen, with zoom controls and pinch-zoom. The focus is
 * centred on load and after each recentering.
 */
export function TreeCanvas({
  layout,
  onSelect,
  onAdd,
  onSiblings,
}: {
  layout: TreeLayout;
  onSelect: (node: TreeNode) => void;
  onAdd: (relation: PlacedAdd['relation']) => void;
  onSiblings: () => void;
}) {
  const { t } = useTranslation(['tree', 'person']);
  const scroller = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  const currentZoom = useEffectEvent(() => zoom);
  const focusId = layout.focus.id;
  const centred = useRef<string | null>(null);

  const offsetX = -layout.bounds.minX + PADDING;
  const width = layout.bounds.maxX - layout.bounds.minX + PADDING * 2;
  const height = layout.bounds.height + PADDING * 2;
  const focusCard = layout.cards.find((card) => card.role === 'focus');

  const focusX = offsetX + (focusCard?.x ?? 0);
  const focusY = PADDING + (focusCard?.y ?? 0);

  // Centre the focus: at once on load, smoothly when recentering (≤ 300 ms, §6.1).
  useLayoutEffect(() => {
    const element = scroller.current;
    if (!element) return;
    const first = centred.current === null;
    centred.current = focusId;
    element.scrollTo({
      left: focusX * currentZoom() - element.clientWidth / 2,
      top: focusY * currentZoom() - element.clientHeight / 2,
      behavior: first || reducedMotion() ? 'auto' : 'smooth',
    });
  }, [focusId, focusX, focusY]);

  // Zooming keeps the middle of the screen where it is.
  const previousZoom = useRef(zoom);
  useLayoutEffect(() => {
    const element = scroller.current;
    const ratio = zoom / previousZoom.current;
    previousZoom.current = zoom;
    if (!element || ratio === 1) return;
    element.scrollTo({
      left: (element.scrollLeft + element.clientWidth / 2) * ratio - element.clientWidth / 2,
      top: (element.scrollTop + element.clientHeight / 2) * ratio - element.clientHeight / 2,
    });
  }, [zoom]);

  // Pinch-zoom on touch screens; one finger keeps panning natively.
  useEffect(() => {
    const element = scroller.current;
    if (!element) return;
    let start: { distance: number; zoom: number } | null = null;
    const distance = (touches: TouchList) => {
      const [a, b] = [touches[0], touches[1]];
      return a && b ? Math.hypot(a.clientX - b.clientX, a.clientY - b.clientY) : 0;
    };
    const onStart = (event: TouchEvent) => {
      if (event.touches.length === 2)
        start = { distance: distance(event.touches), zoom: currentZoom() };
    };
    const onMove = (event: TouchEvent) => {
      if (!start || event.touches.length !== 2 || start.distance === 0) return;
      event.preventDefault();
      setZoom(clampZoom((start.zoom * distance(event.touches)) / start.distance));
    };
    const onEnd = (event: TouchEvent) => {
      if (event.touches.length < 2) start = null;
    };
    element.addEventListener('touchstart', onStart, { passive: true });
    element.addEventListener('touchmove', onMove, { passive: false });
    element.addEventListener('touchend', onEnd);
    return () => {
      element.removeEventListener('touchstart', onStart);
      element.removeEventListener('touchmove', onMove);
      element.removeEventListener('touchend', onEnd);
    };
  }, []);

  const addLabel = {
    parent: t('person:relative.parents'),
    partner: t('person:relative.choices.PARTNER'),
    child: t('person:relative.children'),
  } as const;

  return (
    <div className="relative">
      <div
        ref={scroller}
        data-testid="tree-canvas"
        className="h-[calc(100dvh-14rem)] min-h-80 touch-pan-x touch-pan-y overflow-auto"
      >
        <div className="mx-auto" style={{ width: width * zoom, height: height * zoom }}>
          <div
            className="relative origin-top-left"
            style={{ width, height, transform: `scale(${String(zoom)})` }}
          >
            <div key={focusId} className="absolute inset-0 motion-safe:animate-recenter">
              <svg
                aria-hidden="true"
                className="absolute inset-0 text-border"
                width={width}
                height={height}
              >
                {layout.lines.map(([x1, y1, x2, y2], index) => (
                  <line
                    key={index}
                    x1={x1 + offsetX}
                    y1={y1 + PADDING}
                    x2={x2 + offsetX}
                    y2={y2 + PADDING}
                    stroke="currentColor"
                    strokeWidth={2}
                  />
                ))}
              </svg>
              {layout.cards.map((card) => (
                <div
                  key={card.node.id}
                  className="absolute"
                  data-role={card.role}
                  style={{
                    left: offsetX + card.x - card.width / 2,
                    top: PADDING + card.y - card.height / 2,
                    width: card.width,
                    height: card.height,
                  }}
                >
                  <PersonCard
                    person={card.node}
                    isFocus={card.role === 'focus'}
                    indicator={card.indicator}
                    onSelect={() => {
                      onSelect(card.node);
                    }}
                  />
                </div>
              ))}
              {layout.adds.map((add) => (
                <IconButton
                  key={add.relation}
                  label={addLabel[add.relation]}
                  className="absolute border-dashed"
                  style={{
                    left: offsetX + add.x - ADD_SIZE / 2,
                    top: PADDING + add.y - ADD_SIZE / 2,
                  }}
                  onClick={() => {
                    onAdd(add.relation);
                  }}
                >
                  <PlusIcon />
                </IconButton>
              ))}
              {layout.siblingsChip && (
                <button
                  type="button"
                  onClick={onSiblings}
                  className="absolute inline-flex h-8 -translate-y-1/2 items-center rounded-full border border-border bg-surface px-3 text-caption font-semibold whitespace-nowrap text-primary hover:border-primary focus-visible:outline-2 focus-visible:outline-primary"
                  style={{
                    left: offsetX + layout.siblingsChip.left,
                    top: PADDING + layout.siblingsChip.y,
                  }}
                >
                  {t('tree:siblings', { count: layout.siblings.length })}
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
      <div className="absolute top-2 right-0 flex flex-col gap-2">
        <IconButton
          label={t('tree:zoomIn')}
          disabled={zoom >= MAX_ZOOM}
          onClick={() => {
            setZoom((value) => clampZoom(value + ZOOM_STEP));
          }}
        >
          <PlusIcon />
        </IconButton>
        <IconButton
          label={t('tree:zoomOut')}
          disabled={zoom <= MIN_ZOOM}
          onClick={() => {
            setZoom((value) => clampZoom(value - ZOOM_STEP));
          }}
        >
          <svg
            aria-hidden="true"
            viewBox="0 0 24 24"
            className="size-6"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          >
            <path d="M5 12h14" />
          </svg>
        </IconButton>
      </div>
    </div>
  );
}

function PlusIcon() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 24 24"
      className="size-6"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
    >
      <path d="M12 5v14M5 12h14" />
    </svg>
  );
}
